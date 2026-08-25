package br.ufrn.dpb.server.network;

import br.ufrn.dpb.server.model.UdpMessageHandler;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

public class UDPServer {
    private final int port;
    private final UdpMessageHandler handler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private DatagramSocket socket;

    public UDPServer(int port, UdpMessageHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            this.socket = new DatagramSocket(this.port);
            System.out.println("|Servidor UDP iniciado na porta " + port + "|");

            while (running.get()) {
                byte[] messageBuffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(messageBuffer, messageBuffer.length);
                socket.receive(receivePacket);

                if (handler != null) {
                    byte[] actualData = new byte[receivePacket.getLength()];
                    System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), actualData, 0, receivePacket.getLength());
                    
                    byte[] responseData = handler.onMessage(actualData, receivePacket.getAddress(), receivePacket.getPort());
                    
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

    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
