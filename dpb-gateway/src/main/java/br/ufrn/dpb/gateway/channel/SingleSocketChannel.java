package br.ufrn.dpb.gateway.channel;

import br.ufrn.dpb.gateway.marshaller.Marshaller;
import br.ufrn.dpb.gateway.protocol.Message;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

public class SingleSocketChannel implements Closeable {
    private final Socket socket;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;
    private final Marshaller marshaller = new Marshaller();
    private final ReentrantLock readLock = new ReentrantLock();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ReentrantLock channelLock = new ReentrantLock();

    public SingleSocketChannel(Socket socket, int heartbeatIntervalMs) throws IOException {
        this.socket = socket;
        this.socket.setKeepAlive(true);
        this.socket.setTcpNoDelay(true);
        this.socket.setSoTimeout(0);
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.inputStream = new DataInputStream(socket.getInputStream());
    }

    public SingleSocketChannel(Socket socket) throws IOException {
        this(socket, 3000);
    }

    public SingleSocketChannel(String host, int port, int heartbeatIntervalMs) throws IOException {
        this(new Socket(host, port), heartbeatIntervalMs);
    }

    public SingleSocketChannel(String host, int port) throws IOException {
        this(new Socket(host, port), 3000);
    }

    public void writeMessage(Message message) throws IOException {
        writeLock.lock();
        try {
            byte[] bytes = marshaller.marshall(message);
            outputStream.writeInt(bytes.length);
            outputStream.write(bytes);
            outputStream.flush();
        } finally {
            writeLock.unlock();
        }
    }

    public Message readMessage() throws IOException {
        readLock.lock();
        try {
            int length = inputStream.readInt();
            if (length <= 0 || length > 65536) {
                throw new IOException("Invalid frame length: " + length);
            }
            byte[] data = new byte[length];
            inputStream.readFully(data);
            return marshaller.unmarshall(data, length);
        } finally {
            readLock.unlock();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public ReentrantLock getLock() {
        return channelLock;
    }
}
