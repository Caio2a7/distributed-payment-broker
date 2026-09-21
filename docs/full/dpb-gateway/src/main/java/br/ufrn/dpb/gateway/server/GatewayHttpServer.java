package br.ufrn.dpb.gateway.server;

import br.ufrn.dpb.gateway.balance.LoadBalancer;
import br.ufrn.dpb.gateway.channel.SingleSocketChannel;
import br.ufrn.dpb.gateway.registry.NodeType;
import br.ufrn.dpb.gateway.registry.ServiceRegistry;
import br.ufrn.dpb.middleware.protocol.Message;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GatewayHttpServer implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(GatewayHttpServer.class.getName());
    private final int port;
    private final ServiceRegistry registry;
    private final LoadBalancer loadBalancer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);
    private ServerSocket serverSocket;
    private Thread serverThread;

    public GatewayHttpServer(int port, ServiceRegistry registry, LoadBalancer loadBalancer) {
        this.port = port;
        this.registry = registry;
        this.loadBalancer = loadBalancer;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            serverThread = new Thread(this, "GatewayHttpServerThread");
            serverThread.start();
            LOGGER.info(() -> "GatewayHttpServer listening on HTTP port " + port);
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
            this.serverSocket = new ServerSocket(this.port, 300);
            while (running.get()) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleHttpClient(socket), "HttpClientHandler").start();
            }
        } catch (SocketException e) {
            if (running.get()) {
                LOGGER.severe(() -> "Socket error in GatewayHttpServer: " + e.getMessage());
            }
        } catch (IOException e) {
            LOGGER.severe(() -> "IO error in GatewayHttpServer: " + e.getMessage());
        } finally {
            stop();
        }
    }

    private void handleHttpClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                sendHttpResponse(out, 400, "{\"error\":\"Empty request\"}");
                return;
            }

            StringTokenizer tokenizer = new StringTokenizer(requestLine);
            if (!tokenizer.hasMoreTokens()) {
                sendHttpResponse(out, 400, "{\"error\":\"Malformed request line\"}");
                return;
            }
            String httpMethod = tokenizer.nextToken().toUpperCase();

            // Pagamentos só são aceitos via POST (Proibido GET em operações financeiras mutáveis)
            if (!httpMethod.equals("POST")) {
                sendHttpResponse(out, 405, "{\"error\":\"Method Not Allowed - Only POST is supported for payment operations\"}");
                return;
            }

            int contentLength = 0;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }

            if (contentLength <= 0) {
                sendHttpResponse(out, 400, "{\"error\":\"Missing or empty body (Content-Length required)\"}");
                return;
            }

            char[] bodyChars = new char[contentLength];
            int read = 0;
            while (read < contentLength) {
                int r = in.read(bodyChars, read, contentLength - read);
                if (r == -1) break;
                read += r;
            }
            String body = new String(bodyChars, 0, read);

            Map<String, String> params = new HashMap<>();
            extractJsonParams(body, params);

            String mid = params.get("mid");
            String cardNumber = params.getOrDefault("cardNumber", params.get("card"));
            String amountStr = params.getOrDefault("amount", params.get("grossAmount"));

            if (mid == null || cardNumber == null || amountStr == null) {
                sendHttpResponse(out, 400, "{\"error\":\"Required fields missing: mid, cardNumber, amount\"}");
                return;
            }

            long grossAmount = Long.parseLong(amountStr);
            long installments = Long.parseLong(params.getOrDefault("installments", "1"));
            int expMonth = Integer.parseInt(params.getOrDefault("expMonth", params.getOrDefault("month", "12")));
            int expYear = Integer.parseInt(params.getOrDefault("expYear", params.getOrDefault("year", "2028")));
            byte opCode = (byte) (params.getOrDefault("operation", "PAYMENT").equalsIgnoreCase("REFUND") ? 1 : 0);

            byte[] binaryPayload = buildBinaryPayload(mid, opCode, cardNumber, expMonth, expYear, grossAmount, installments);
            int reqId = requestIdCounter.getAndIncrement();
            Message requestMessage = new Message(Message.Type.REQUEST, reqId, binaryPayload);

            List<SingleSocketChannel> authorizers = registry.getChannels(NodeType.AUTHORIZER);
            if (authorizers.isEmpty()) {
                sendHttpResponse(out, 503, "{\"error\":\"No authorizer instances available\"}");
                return;
            }

            SingleSocketChannel chosenAuthorizer = loadBalancer.choose(authorizers);
            chosenAuthorizer.writeMessage(requestMessage);
            Message response = chosenAuthorizer.readMessage();
            while (response != null && response.getType() == Message.Type.HEARTBEAT_ACK) {
                response = chosenAuthorizer.readMessage();
            }

            if (response.getType() == Message.Type.RESPONSE) {
                ByteBuffer respBuf = ByteBuffer.wrap(response.getPayload());
                int fpLen = Byte.toUnsignedInt(respBuf.get());
                byte[] fpBytes = new byte[fpLen];
                respBuf.get(fpBytes);
                String fingerprint = new String(fpBytes, StandardCharsets.UTF_8);
                byte statusCode = respBuf.get();
                long liquidAmount = respBuf.getLong();

                String statusStr = switch (statusCode) {
                    case 1 -> "PENDING";
                    case 2 -> "REJECTED";
                    case 3 -> "ACCEPTED";
                    case 4 -> "COMPLETED";
                    case 5 -> "FAILED";
                    default -> "UNKNOWN";
                };

                String jsonResponse = String.format(Locale.US,
                        "{\"requestId\":%d,\"status\":\"%s\",\"grossAmount\":%d,\"liquidAmount\":%d,\"fingerprint\":\"%s\",\"mid\":\"%s\"}",
                        reqId, statusStr, grossAmount, liquidAmount, fingerprint, mid);

                sendHttpResponse(out, 200, jsonResponse);
            } else {
                String errorMsg = new String(response.getPayload(), StandardCharsets.UTF_8);
                sendHttpResponse(out, 400, "{\"error\":\"" + errorMsg.replace("\"", "\\\"") + "\"}");
            }

        } catch (Exception e) {
            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                sendHttpResponse(out, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            } catch (Exception ignored) {
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void extractJsonParams(String json, Map<String, String> params) {
        Pattern pattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\"[^\"]*\"|\\d+|true|false)");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2).replace("\"", "").trim();
            params.put(key, value);
        }
    }

    private byte[] buildBinaryPayload(String mid, byte opCode, String cardNumber, int expMonth, int expYear, long grossAmount, long installments) {
        byte[] midBytes = mid.getBytes(StandardCharsets.UTF_8);
        byte[] cardBytes = cardNumber.getBytes(StandardCharsets.UTF_8);

        int totalSize = 1 + midBytes.length + 1 + 1 + cardBytes.length + Integer.BYTES + Integer.BYTES + Long.BYTES + Long.BYTES;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.put((byte) midBytes.length);
        buffer.put(midBytes);
        buffer.put(opCode);
        buffer.put((byte) cardBytes.length);
        buffer.put(cardBytes);
        buffer.putInt(expMonth);
        buffer.putInt(expYear);
        buffer.putLong(grossAmount);
        buffer.putLong(installments);
        return buffer.array();
    }

    private void sendHttpResponse(DataOutputStream out, int statusCode, String jsonBody) throws IOException {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        String statusText = (statusCode == 200) ? "200 OK" : (statusCode == 400) ? "400 Bad Request" : (statusCode == 405) ? "405 Method Not Allowed" : (statusCode == 503) ? "503 Service Unavailable" : "500 Internal Server Error";

        out.writeBytes("HTTP/1.1 " + statusText + "\r\n");
        out.writeBytes("Server: DPB-Gateway\r\n");
        out.writeBytes("Content-Type: application/json; charset=UTF-8\r\n");
        out.writeBytes("Content-Length: " + bodyBytes.length + "\r\n");
        out.writeBytes("Connection: close\r\n");
        out.writeBytes("\r\n");
        out.write(bodyBytes);
        out.flush();
    }
}
