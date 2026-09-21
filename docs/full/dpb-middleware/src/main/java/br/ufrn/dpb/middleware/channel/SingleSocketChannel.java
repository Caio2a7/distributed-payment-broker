package br.ufrn.dpb.middleware.channel;

import br.ufrn.dpb.middleware.marshaller.Marshaller;
import br.ufrn.dpb.middleware.protocol.Message;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class SingleSocketChannel implements Closeable {
    private final Socket socket;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;
    private final Marshaller marshaller = new Marshaller();
    private final Object readLock = new Object();
    private final Object writeLock = new Object();

    public SingleSocketChannel(Socket socket, int heartbeatIntervalMs) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(heartbeatIntervalMs * 10);
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
        synchronized (writeLock) {
            byte[] bytes = marshaller.marshall(message);
            outputStream.writeInt(bytes.length);
            outputStream.write(bytes);
            outputStream.flush();
        }
    }

    public Message readMessage() throws IOException {
        synchronized (readLock) {
            int length = inputStream.readInt();
            byte[] data = new byte[length];
            inputStream.readFully(data);
            return marshaller.unmarshall(data, length);
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
}
