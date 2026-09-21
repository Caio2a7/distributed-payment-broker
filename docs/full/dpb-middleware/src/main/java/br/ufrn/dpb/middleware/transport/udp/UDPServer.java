package br.ufrn.dpb.middleware.transport.udp;

import br.ufrn.dpb.middleware.invoker.Invoker;
import br.ufrn.dpb.middleware.transport.ServerRequestHandler;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class UDPServer implements ServerRequestHandler {
    private final int port;
    private final Invoker handler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private DatagramSocket socket;
    private static final Logger LOGGER = Logger.getLogger(UDPServer.class.getName());

    public UDPServer(int port, Invoker handler) {
        this.port = port;
        this.handler = handler;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            this.socket = new DatagramSocket(this.port);
            LOGGER.info(() -> "UDP server listening on port " + port);

            while (running.get()) {
                byte[] messageBuffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(messageBuffer, messageBuffer.length);
                socket.receive(receivePacket);

                if (handler != null) {
                    byte[] responseData = handler.invoke(receivePacket.getData(), receivePacket.getLength());
                    
                    if (responseData != null && responseData.length > 0) {
                        DatagramPacket sendPacket = new DatagramPacket(
                                responseData,
                                responseData.length,
                                receivePacket.getAddress(),
                                receivePacket.getPort()
                        );
                        socket.send(sendPacket);
                    }
                }
            }
        } catch (SocketException exception) {
            if (running.get()) {
                exception.printStackTrace();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        } finally {
            stop();
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
