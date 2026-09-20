package br.ufrn.dpb.ledger;

import br.ufrn.dpb.ledger.channel.SingleSocketChannel;
import br.ufrn.dpb.ledger.controller.LedgerController;
import br.ufrn.dpb.ledger.model.Merchant;
import br.ufrn.dpb.ledger.protocol.Message;
import br.ufrn.dpb.ledger.queue.SingularUpdateQueue;
import br.ufrn.dpb.ledger.repository.InMemoryMerchantRepository;
import br.ufrn.dpb.ledger.repository.MerchantRepository;
import br.ufrn.dpb.ledger.service.LedgerService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        String gatewayHost = args.length > 0 ? args[0] : System.getenv().getOrDefault("GATEWAY_HOST", "localhost");
        int gatewayPort = args.length > 1 ? Integer.parseInt(args[1]) : Integer.parseInt(System.getenv().getOrDefault("GATEWAY_PORT", "9090"));

        System.setProperty("java.util.logging.SimpleFormatter.format",
            "%1$tT.%1$tL [\u001B[34mLEDGER\u001B[0m] [\u001B[34m%4$s\u001B[0m] %3$s - %5$s%6$s%n");
        Logger.getLogger("").setLevel(Level.INFO);

        MerchantRepository merchantRepository = new InMemoryMerchantRepository();
        merchantRepository.save(new Merchant("100202604812345", "O Boticário", "77388007000157", 50_000_000L));
        merchantRepository.save(new Merchant("100202607965432", "Cacau Show", "04770521000144", 25_000_000L));
        merchantRepository.save(new Merchant("100202608409160", "Supermercados Nordestão", "08040916000109", 100_000_000L));
        merchantRepository.save(new Merchant("100202609745875", "Farmácias Globo", "07458751000190", 15_000_000L));
        merchantRepository.save(new Merchant("100202603927547", "Lojas Renner", "92754738000156", 80_000_000L));

        LedgerService ledgerService = new LedgerService(merchantRepository);
        LedgerController ledgerController = new LedgerController(ledgerService);

        SingularUpdateQueue updateQueue = new SingularUpdateQueue(ledgerController);
        updateQueue.start();

        ExecutorService receiverPool = Executors.newVirtualThreadPerTaskExecutor();

        LOGGER.info(() -> "Connecting to Gateway at " + gatewayHost + ":" + gatewayPort);

        while (true) {
            try (SingleSocketChannel channel = new SingleSocketChannel(gatewayHost, gatewayPort)) {
                Message regMsg = new Message(Message.Type.REGISTER, 0, "LEDGER".getBytes(StandardCharsets.UTF_8));
                channel.writeMessage(regMsg);

                Message ack = channel.readMessage();
                if (ack.getType() != Message.Type.REGISTER_ACK) {
                    throw new IllegalStateException("Failed to register with Gateway: unexpected ack " + ack.getType());
                }
                LOGGER.info(() -> "Ledger successfully registered with Gateway. Entering event loop...");

                while (channel.isConnected()) {
                    Message msg = channel.readMessage();

                    if (msg.getType() == Message.Type.HEARTBEAT) {
                        // Heartbeat do Gateway mantem a conexao ativa
                    } else if (msg.getType() == Message.Type.REQUEST) {
                        receiverPool.submit(() -> updateQueue.submit(msg, channel));
                    }
                }
            } catch (Exception e) {
                LOGGER.warning(() -> "Ledger disconnected (" + e.getMessage() + "). Reconnecting in 2s...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }
}
