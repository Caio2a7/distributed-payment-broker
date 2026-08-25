
package br.imd.ufrn;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

public class NIOWebServer {

    private static Selector selector = null;
    private static final int BUFFER_SIZE = 1024;

    public static void main(String args[]) throws IOException {
        System.out.println("Meu NIO Webserver Started");
        selector = Selector.open();

        ServerSocketChannel serversocketchannel = ServerSocketChannel.open();
        serversocketchannel.socket().bind(new InetSocketAddress(9999));
        serversocketchannel.configureBlocking(false);
        serversocketchannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> i = selectedKeys.iterator();

            while (i.hasNext()) {
                SelectionKey key = i.next();

                if (key.isAcceptable()) {
                    processAcceptEvent(serversocketchannel, key);
                } else if (key.isReadable()) {
                    processReadEvent(key);
                }
                i.remove();
            }
        }
    }

    private static void processAcceptEvent(ServerSocketChannel mySocket,
            SelectionKey key) throws IOException {
        System.out.println("Conexão aceita...");
        // aceita a conexão do cliente e faz ele não-bloqueante
        SocketChannel myClient = mySocket.accept();
        myClient.configureBlocking(false);
        // Registra o canal do cliente com o seletor para ler
        myClient.register(selector, SelectionKey.OP_READ);
    }

    private static void processReadEvent(SelectionKey key)
            throws IOException {
        System.out.println("Processando evento de leitura...");
        SocketChannel myClient = (SocketChannel) key.channel();
        ByteBuffer myBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        myClient.read(myBuffer);
        String headerLine = new String(myBuffer.array()).trim();
        if (headerLine.length() > 0) {
            StringTokenizer tokenizer = new StringTokenizer(headerLine);
            String httpMethod = tokenizer.nextToken();
            if (httpMethod.equals("GET")) {
                System.out.println("Processando o método GET");
                StringBuilder responseBuffer = new StringBuilder();
                responseBuffer
                        .append("<html><h1>WebServer Home Page.... </h1><br>")
                        .append("<b>Bem vindo ao Meu web server! </b><BR>")
                        .append("</html>");
                sendResponse(myClient, 200, responseBuffer.toString());
            } else {
                System.out.println("The HTTP method is not recognized");
                sendResponse(myClient, 405, "Method Not Allowed");
            }
        }
    }

    public static void sendResponse(SocketChannel socket, int statusCode, String responseString) {
        String statusLine;
        String serverHeader = "Server: WebServer\r\n";
        String contentTypeHeader = "Content-Type: text/html\r\n";

        try {
            StringBuilder response = new StringBuilder();

            if (statusCode == 200) {
                statusLine = "HTTP/1.1 200 OK\r\n";
                String contentLengthHeader = "Content-Length: " + responseString.getBytes().length + "\r\n";
                response.append(statusLine);
                response.append(serverHeader);
                response.append(contentTypeHeader);
                response.append(contentLengthHeader);
                response.append("\r\n");
                response.append(responseString);
            } else if (statusCode == 405) {
                statusLine = "HTTP/1.1 405 Method Not Allowed\r\n";
                response.append(statusLine).append("\r\n");
            } else {
                statusLine = "HTTP/1.1 404 Not Found\r\n";
                response.append(statusLine).append("\r\n");
            }
            // Converte para bytes e escreve no canal
            ByteBuffer buffer = ByteBuffer.wrap(response.toString().getBytes());
            while (buffer.hasRemaining()) {
                socket.write(buffer);
            }
            socket.close(); // fecha a conexão após resposta simples
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
