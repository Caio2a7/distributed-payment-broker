package br.ufrn.dpb.gateway.channel;

import java.io.IOException;
import java.net.Socket;

public class SingleSocketChannel extends br.ufrn.dpb.middleware.channel.SingleSocketChannel {
    public SingleSocketChannel(Socket socket, int heartbeatIntervalMs) throws IOException {
        super(socket, heartbeatIntervalMs);
    }

    public SingleSocketChannel(Socket socket) throws IOException {
        super(socket);
    }
}
