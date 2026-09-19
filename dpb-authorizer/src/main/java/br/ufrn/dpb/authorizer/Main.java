package br.ufrn.dpb.authorizer;

import br.ufrn.dpb.authorizer.channel.SingleSocketChannel;
import br.ufrn.dpb.authorizer.controller.PaymentController;
import br.ufrn.dpb.authorizer.protocol.Message;
import br.ufrn.dpb.authorizer.repository.InMemoryTransactionRepository;
import br.ufrn.dpb.authorizer.repository.TransactionRepository;
import br.ufrn.dpb.authorizer.service.PaymentService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        String gatewayHost = args.length > 0 ? args[0] : System.getenv().getOrDefault("GATEWAY_HOST", "localhost");
        int gatewayPort = args.length > 1 ? Integer.parseInt(args[1]) : Integer.parseInt(System.getenv().getOrDefault("GATEWAY_PORT", "9090"));
        String hmacSecret = "chave-secreta-broker-ufrn-2026";

        System.setProperty("java.util.logging.SimpleFormatter.format",
            "%1$tT.%1$tL [\u001B[32mAUTHORIZER\u001B[0m] [\u001B[32m%4$s\u001B[0m] %3$s - %5$s%6$s%n");
        Logger.getLogger("").setLevel(Level.INFO);

        TransactionRepository transactionRepository = new InMemoryTransactionRepository();
        PaymentService paymentService = new PaymentService(transactionRepository, hmacSecret, gatewayHost, gatewayPort);
        PaymentController paymentController = new PaymentController(paymentService);

        LOGGER.info(() -> "Connecting to Gateway at " + gatewayHost + ":" + gatewayPort);

        while (true) {
            try (SingleSocketChannel channel = new SingleSocketChannel(gatewayHost, gatewayPort)) {
                Message regMsg = new Message(Message.Type.REGISTER, 0, "AUTHORIZER".getBytes(StandardCharsets.UTF_8));
                channel.writeMessage(regMsg);

                Message ack = channel.readMessage();
                if (ack.getType() != Message.Type.REGISTER_ACK) {
                    throw new IllegalStateException("Failed to register with Gateway: unexpected ack " + ack.getType());
                }
                LOGGER.info(() -> "Authorizer successfully registered with Gateway. Entering event loop...");

                while (channel.isConnected()) {
                    Message msg = channel.readMessage();

                    if (msg.getType() == Message.Type.HEARTBEAT) {
                        // Heartbeat do Gateway mantem a conexao ativa
                    } else if (msg.getType() == Message.Type.REQUEST) {
                        try {
                            byte[] responsePayload = paymentController.handlePayment(msg.getPayload());
                            channel.writeMessage(new Message(Message.Type.RESPONSE, msg.getRequestId(), responsePayload));
                        } catch (Exception e) {
                            byte[] errorPayload = (e.getMessage() != null ? e.getMessage() : "Authorizer error").getBytes(StandardCharsets.UTF_8);
                            channel.writeMessage(new Message(Message.Type.ERROR, msg.getRequestId(), errorPayload));
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warning(() -> "Authorizer disconnected (" + e.getMessage() + "). Reconnecting in 2s...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }
}
