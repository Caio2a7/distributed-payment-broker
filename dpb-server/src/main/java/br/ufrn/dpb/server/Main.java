package br.ufrn.dpb.server;

import br.ufrn.dpb.server.controller.PaymentController;
import br.ufrn.dpb.server.network.UDPServer;
import br.ufrn.dpb.server.repository.AccountRepository;
import br.ufrn.dpb.server.repository.InMemoryAccountRepository;
import br.ufrn.dpb.server.service.PaymentService;

public class Main {
    public static void main(String[] args) {
        int port = 8080;
        String hmacSecret = "12345";

        System.setProperty("java.util.logging.SimpleFormatter.format",
            "%1$tT.%1$tL [\u001B[32m%4$s\u001B[0m] %3$s - %5$s%6$s%n");

        Logger.getLogger("").setLevel(Level.OFF);

        AccountRepository accountRepository = new InMemoryAccountRepository();
        PaymentService paymentService = new PaymentService(accountRepository);
        PaymentController paymentController = new PaymentController(paymentService);
        UDPServer udpServer = new UDPServer(port, paymentController);
        accountRepository.create(10000L);
        accountRepository.create(1000L);

        udpServer.start();
    }
}
