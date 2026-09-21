package br.ufrn.dpb.gateway.channel;

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

public class GatewayNodeListener implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(GatewayNodeListener.class.getName());
    private final int port;
    private final ServiceRegistry registry;
    private final int heartbeatIntervalMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread listenerThread;

    public GatewayNodeListener(int port, ServiceRegistry registry, int heartbeatIntervalMs) {
        this.port = port;
        this.registry = registry;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public GatewayNodeListener(int port, ServiceRegistry registry) {
        this(port, registry, 3000);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            listenerThread = new Thread(this, "GatewayNodeListenerThread");
            listenerThread.start();
            LOGGER.info(() -> "GatewayNodeListener listening on TCP port " + port);
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
                handleNewConnection(socket);
            }
        } catch (IOException e) {
            if (running.get()) {
                LOGGER.severe(() -> "Error in GatewayNodeListener: " + e.getMessage());
            }
        }
    }

    private void handleNewConnection(Socket socket) {
        new Thread(() -> {
            try {
                SingleSocketChannel channel = new SingleSocketChannel(socket, this.heartbeatIntervalMs);
                Message msg = channel.readMessage();

                if (msg.getType() == Message.Type.REGISTER) {
                    String typeStr = new String(msg.getPayload(), StandardCharsets.UTF_8);
                    NodeType nodeType = NodeType.fromString(typeStr);
                    registry.register(nodeType, channel);

                    Message ack = new Message(Message.Type.REGISTER_ACK, msg.getRequestId(), new byte[0]);
                    channel.writeMessage(ack);
                } else {
                    while (channel.isConnected()) {
                        if (msg.getType() == Message.Type.LEDGER_REQUEST) {
                            List<SingleSocketChannel> ledgers = registry.getChannels(NodeType.LEDGER);
                            if (ledgers.isEmpty()) {
                                Message error = new Message(Message.Type.ERROR, msg.getRequestId(),
                                        "Ledger not available".getBytes(StandardCharsets.UTF_8));
                                channel.writeMessage(error);
                            } else {
                                SingleSocketChannel ledgerChannel = ledgers.get(0);
                                Message ledgerReq = new Message(Message.Type.REQUEST, msg.getRequestId(), msg.getPayload());
                                ledgerChannel.writeMessage(ledgerReq);
                                Message ledgerResp = ledgerChannel.readMessage();
                                Message resp = new Message(Message.Type.LEDGER_RESPONSE, msg.getRequestId(), ledgerResp.getPayload());
                                channel.writeMessage(resp);
                            }
                        }
                        msg = channel.readMessage();
                    }
                }
            } catch (Exception e) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }, "NodeConnectionHandler").start();
    }
}
