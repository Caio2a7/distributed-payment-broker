package br.ufrn.dpb.gateway.server;

import java.util.concurrent.Executors;

import br.ufrn.dpb.gateway.balance.LoadBalancer;
import br.ufrn.dpb.gateway.channel.SingleSocketChannel;
import br.ufrn.dpb.gateway.marshaller.Marshaller;
import br.ufrn.dpb.gateway.protocol.Message;
import br.ufrn.dpb.gateway.registry.NodeType;
import br.ufrn.dpb.gateway.registry.ServiceRegistry;
import io.grpc.*;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class GatewayGrpcServer {
    private static final Logger LOGGER = Logger.getLogger(GatewayGrpcServer.class.getName());
    private final int port;
    private final ServiceRegistry registry;
    private final LoadBalancer loadBalancer;
    private final Marshaller marshaller = new Marshaller();
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);
    private Server server;

    public GatewayGrpcServer(int port, ServiceRegistry registry, LoadBalancer loadBalancer) {
        this.port = port;
        this.registry = registry;
        this.loadBalancer = loadBalancer;
    }

    public void start() throws IOException {
        MethodDescriptor.Marshaller<byte[]> byteMarshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(byte[] value) {
                return new ByteArrayInputStream(value);
            }

            @Override
            public byte[] parse(InputStream stream) {
                try {
                    return stream.readAllBytes();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read gRPC stream bytes", e);
                }
            }
        };

        MethodDescriptor<byte[], byte[]> streamMethod = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("payment.PaymentService", "ProcessPaymentStream"))
                .setRequestMarshaller(byteMarshaller)
                .setResponseMarshaller(byteMarshaller)
                .build();

        ServerServiceDefinition serviceDefinition = ServerServiceDefinition.builder("payment.PaymentService")
                .addMethod(streamMethod, ServerCalls.asyncBidiStreamingCall(new ServerCalls.BidiStreamingMethod<byte[], byte[]>() {
                    @Override
                    public StreamObserver<byte[]> invoke(StreamObserver<byte[]> responseObserver) {
                        return new StreamObserver<>() {
                            private final Object streamLock = new Object();
                            private boolean clientFinished = false;
                            private int activeRequests = 0;

                            @Override
                            public void onNext(byte[] clientBytes) {
                                synchronized (streamLock) {
                                    activeRequests++;
                                }
                                int reqId = requestIdCounter.getAndIncrement();
                                try {
                                    List<SingleSocketChannel> authorizers = registry.getChannels(NodeType.AUTHORIZER);
                                    if (authorizers.isEmpty()) {
                                        byte[] errPayload = "No authorizer instances available".getBytes(StandardCharsets.UTF_8);
                                        Message errorMsg = new Message(Message.Type.ERROR, reqId, errPayload);
                                        sendResponseSafe(marshaller.marshall(errorMsg));
                                        return;
                                    }

                                    SingleSocketChannel chosenAuthorizer = loadBalancer.choose(authorizers);
                                    Message requestMessage;
                                    try {
                                        requestMessage = marshaller.unmarshall(clientBytes, clientBytes.length);
                                    } catch (Exception e) {
                                        requestMessage = new Message(Message.Type.REQUEST, reqId, clientBytes);
                                    }

                                    Message response;
                                    chosenAuthorizer.getLock().lock();
                                    try {
                                        chosenAuthorizer.writeMessage(requestMessage);
                                        response = chosenAuthorizer.readMessage();
                                        while (response != null && response.getType() == Message.Type.HEARTBEAT_ACK) {
                                            response = chosenAuthorizer.readMessage();
                                        }
                                    } finally {
                                        chosenAuthorizer.getLock().unlock();
                                    }

                                    sendResponseSafe(marshaller.marshall(response));
                                } catch (Exception e) {
                                    byte[] errPayload = ("gRPC routing error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
                                    Message errorMsg = new Message(Message.Type.ERROR, reqId, errPayload);
                                    sendResponseSafe(marshaller.marshall(errorMsg));
                                }
                            }

                            private void sendResponseSafe(byte[] data) {
                                synchronized (streamLock) {
                                    try {
                                        responseObserver.onNext(data);
                                    } catch (Exception ignored) {
                                    } finally {
                                        activeRequests--;
                                        if (clientFinished && activeRequests <= 0) {
                                            try {
                                                responseObserver.onCompleted();
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onError(Throwable t) {
                                if (t != null && !t.getMessage().contains("CANCELLED")) {
                                    LOGGER.warning(() -> "gRPC client stream error: " + t.getMessage());
                                }
                            }

                            @Override
                            public void onCompleted() {
                                synchronized (streamLock) {
                                    clientFinished = true;
                                    if (activeRequests <= 0) {
                                        try {
                                            responseObserver.onCompleted();
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                        };
                    }
                }))
                .build();

        this.server = ServerBuilder.forPort(this.port)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .addService(serviceDefinition)
                .build()
                .start();

        LOGGER.info(() -> "GatewayGrpcServer listening with Bidi Streaming on gRPC port " + port);
    }

    public void stop() {
        if (server != null && !server.isShutdown()) {
            server.shutdown();
        }
    }
}
