package br.ufrn.dpb.gateway.server;

import br.ufrn.dpb.gateway.balance.LoadBalancer;
import br.ufrn.dpb.gateway.channel.SingleSocketChannel;
import br.ufrn.dpb.gateway.registry.NodeType;
import br.ufrn.dpb.gateway.registry.ServiceRegistry;
import br.ufrn.dpb.middleware.protocol.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class GatewayTcpServer implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(GatewayTcpServer.class.getName());
    private final int port;
    private final ServiceRegistry registry;
    private final LoadBalancer loadBalancer;
    private final int heartbeatIntervalMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread serverThread;

    public GatewayTcpServer(int port, ServiceRegistry registry, LoadBalancer loadBalancer, int heartbeatIntervalMs) {
        this.port = port;
        this.registry = registry;
        this.loadBalancer = loadBalancer;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            serverThread = new Thread(this, "GatewayTcpServerThread");
            serverThread.start();
            LOGGER.info(() -> "GatewayTcpServer listening on TCP port " + port);
        }
    }

    public void stop() {
        running.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void run() {
        try {
            this.serverSocket = new ServerSocket(this.port);
            while (running.get()) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleConnection(socket), "TcpConnectionHandler").start();
            }
        } catch (IOException e) {
            if (running.get()) {
                LOGGER.severe(() -> "Error in GatewayTcpServer: " + e.getMessage());
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            SingleSocketChannel channel = new SingleSocketChannel(socket, this.heartbeatIntervalMs);
            Message firstMsg = channel.readMessage();

            if (firstMsg.getType() == Message.Type.REGISTER) {
                String typeStr = new String(firstMsg.getPayload(), StandardCharsets.UTF_8);
                NodeType nodeType = NodeType.fromString(typeStr);
                registry.register(nodeType, channel);

                Message ack = new Message(Message.Type.REGISTER_ACK, firstMsg.getRequestId(), new byte[0]);
                channel.writeMessage(ack);

            } else if (firstMsg.getType() == Message.Type.LEDGER_REQUEST) {
                handleInternalLedgerLoop(channel, firstMsg);

            } else {
                handleClientLoop(channel, firstMsg);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleClientLoop(SingleSocketChannel clientChannel, Message initialMessage) {
        try {
            Message currentMsg = initialMessage;
            while (clientChannel.isConnected() && currentMsg != null) {
                int requestId = currentMsg.getRequestId();
                List<SingleSocketChannel> authorizers = registry.getChannels(NodeType.AUTHORIZER);

                if (authorizers.isEmpty()) {
                    byte[] err = "No authorizer instances available".getBytes(StandardCharsets.UTF_8);
                    clientChannel.writeMessage(new Message(Message.Type.ERROR, requestId, err));
                } else {
                    SingleSocketChannel chosenAuthorizer = loadBalancer.choose(authorizers);
                    try {
                        chosenAuthorizer.writeMessage(currentMsg);
                        Message response = chosenAuthorizer.readMessage();
                        while (response != null && response.getType() == Message.Type.HEARTBEAT_ACK) {
                            response = chosenAuthorizer.readMessage();
                        }
                        clientChannel.writeMessage(response);
                    } catch (IOException e) {
                        registry.unregister(chosenAuthorizer, "Connection broken during client routing (" + e.getMessage() + ")");
                        byte[] err = "Failed to communicate with Authorizer node".getBytes(StandardCharsets.UTF_8);
                        clientChannel.writeMessage(new Message(Message.Type.ERROR, requestId, err));
                    }
                }
                currentMsg = clientChannel.readMessage();
            }
        } catch (Exception ignored) {
        }
    }

    private void handleInternalLedgerLoop(SingleSocketChannel channel, Message initialMessage) {
        try {
            Message currentMsg = initialMessage;
            while (channel.isConnected() && currentMsg != null) {
                if (currentMsg.getType() == Message.Type.LEDGER_REQUEST) {
                    List<SingleSocketChannel> ledgers = registry.getChannels(NodeType.LEDGER);
                    if (ledgers.isEmpty()) {
                        byte[] err = "Ledger not available".getBytes(StandardCharsets.UTF_8);
                        channel.writeMessage(new Message(Message.Type.ERROR, currentMsg.getRequestId(), err));
                    } else {
                        SingleSocketChannel ledgerChannel = ledgers.get(0);
                        try {
                            Message ledgerReq = new Message(Message.Type.REQUEST, currentMsg.getRequestId(), currentMsg.getPayload());
                            ledgerChannel.writeMessage(ledgerReq);
                            Message ledgerResp = ledgerChannel.readMessage();
                            while (ledgerResp != null && ledgerResp.getType() == Message.Type.HEARTBEAT_ACK) {
                                ledgerResp = ledgerChannel.readMessage();
                            }
                            Message resp = new Message(Message.Type.LEDGER_RESPONSE, currentMsg.getRequestId(), ledgerResp.getPayload());
                            channel.writeMessage(resp);
                        } catch (IOException e) {
                            registry.unregister(ledgerChannel, "Connection broken during internal ledger routing (" + e.getMessage() + ")");
                            byte[] err = "Ledger node disconnected".getBytes(StandardCharsets.UTF_8);
                            channel.writeMessage(new Message(Message.Type.ERROR, currentMsg.getRequestId(), err));
                        }
                    }
                }
                currentMsg = channel.readMessage();
            }
        } catch (Exception ignored) {
        }
    }
}
