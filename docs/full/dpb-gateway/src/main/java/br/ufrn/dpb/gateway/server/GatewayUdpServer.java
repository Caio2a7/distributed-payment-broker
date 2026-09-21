package br.ufrn.dpb.gateway.server;

import br.ufrn.dpb.gateway.balance.LoadBalancer;
import br.ufrn.dpb.gateway.channel.SingleSocketChannel;
import br.ufrn.dpb.gateway.registry.NodeType;
import br.ufrn.dpb.gateway.registry.ServiceRegistry;
import br.ufrn.dpb.middleware.marshaller.Marshaller;
import br.ufrn.dpb.middleware.protocol.Message;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class GatewayUdpServer implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(GatewayUdpServer.class.getName());
    private final int port;
    private final ServiceRegistry registry;
    private final LoadBalancer loadBalancer;
    private final Marshaller marshaller = new Marshaller();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private DatagramSocket socket;
    private Thread serverThread;

    public GatewayUdpServer(int port, ServiceRegistry registry, LoadBalancer loadBalancer) {
        this.port = port;
        this.registry = registry;
        this.loadBalancer = loadBalancer;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            serverThread = new Thread(this, "GatewayUdpServerThread");
            serverThread.start();
            LOGGER.info(() -> "GatewayUdpServer listening for clients on UDP port " + port);
        }
    }

    public void stop() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public void run() {
        try {
            this.socket = new DatagramSocket(this.port);

            while (running.get()) {
                byte[] buffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);

                byte[] packetData = new byte[receivePacket.getLength()];
                System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), packetData, 0, receivePacket.getLength());
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                new Thread(() -> handleClientPacket(packetData, clientAddress, clientPort), "UdpClientHandler").start();
            }
        } catch (SocketException e) {
            if (running.get()) {
                LOGGER.severe(() -> "Socket error in GatewayUdpServer: " + e.getMessage());
            }
        } catch (IOException e) {
            LOGGER.severe(() -> "IO error in GatewayUdpServer: " + e.getMessage());
        } finally {
            stop();
        }
    }

    private void handleClientPacket(byte[] packetData, InetAddress clientAddress, int clientPort) {
        int requestId = 0;
        try {
            Message request = marshaller.unmarshall(packetData, packetData.length);
            requestId = request.getRequestId();

            List<SingleSocketChannel> authorizers = registry.getChannels(NodeType.AUTHORIZER);
            if (authorizers.isEmpty()) {
                sendErrorResponse(clientAddress, clientPort, requestId, "No authorizer instances available");
                return;
            }

            SingleSocketChannel chosenChannel = loadBalancer.choose(authorizers);

            chosenChannel.writeMessage(request);
            Message response = chosenChannel.readMessage();
            while (response != null && response.getType() == Message.Type.HEARTBEAT_ACK) {
                response = chosenChannel.readMessage();
            }

            byte[] responseBytes = marshaller.marshall(response);
            DatagramPacket sendPacket = new DatagramPacket(
                    responseBytes,
                    responseBytes.length,
                    clientAddress,
                    clientPort
            );
            socket.send(sendPacket);

        } catch (Exception e) {
            LOGGER.warning(() -> "Error routing request to authorizer: " + e.getMessage());
            sendErrorResponse(clientAddress, clientPort, requestId, "Gateway routing error: " + e.getMessage());
        }
    }

    private void sendErrorResponse(InetAddress clientAddress, int clientPort, int requestId, String message) {
        try {
            byte[] errorPayload = message.getBytes(StandardCharsets.UTF_8);
            Message errorMsg = new Message(Message.Type.ERROR, requestId, errorPayload);
            byte[] errorBytes = marshaller.marshall(errorMsg);

            DatagramPacket sendPacket = new DatagramPacket(
                    errorBytes,
                    errorBytes.length,
                    clientAddress,
                    clientPort
            );
            socket.send(sendPacket);
        } catch (Exception ignored) {
        }
    }
}
