package br.ufrn.dpb.server;

import java.math.BigDecimal;
import br.ufrn.dpb.server.controller.PaymentController;
import br.ufrn.dpb.server.network.UDPServer;
import br.ufrn.dpb.server.repository.AccountRepository;
import br.ufrn.dpb.server.repository.InMemoryAccountRepository;
import br.ufrn.dpb.server.service.PaymentService;

public class Main {
    public static void main(String[] args) {
        int port = 8080;

        AccountRepository accountRepository = new InMemoryAccountRepository();
        PaymentService paymentService = new PaymentService(accountRepository);
        PaymentController paymentController = new PaymentController(paymentService);
        UDPServer udpServer = new UDPServer(port, paymentController);
        accountRepository.create(BigDecimal.valueOf(10000));
        accountRepository.create(BigDecimal.valueOf(5000));

        udpServer.start();
    }
}
