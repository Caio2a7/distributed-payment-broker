package br.ufrn.dpb.server;

import br.ufrn.dpb.server.model.Account;
import br.ufrn.dpb.server.repository.InMemoryAccountRepository;
import br.ufrn.dpb.server.service.PaymentService;

import java.math.BigDecimal;

public class PaymentBroker {

    public static void main(String[] args) {
        var repository = new InMemoryAccountRepository();
        var paymentService = new PaymentService(repository);

        Long aliceId = repository.nextId();
        Long bobId = repository.nextId();

        repository.save(new Account(aliceId, "Alice", new BigDecimal("1000.00")));
        repository.save(new Account(bobId, "Bob", new BigDecimal("0.00")));

        printBalances(repository, aliceId, bobId);

        var tx1 = paymentService.transfer(aliceId, bobId, new BigDecimal("150.00"), "key-1");
        System.out.println("tx1 status=" + tx1.getStatus());
        printBalances(repository, aliceId, bobId);

        var tx2 = paymentService.transfer(aliceId, bobId, new BigDecimal("150.00"), "key-1");
        System.out.println("idempotente? " + tx1.getId().equals(tx2.getId()));

        try {
            paymentService.transfer(aliceId, bobId, new BigDecimal("999999.00"), "key-2");
        } catch (Exception e) {
            System.out.println("erro esperado: " + e.getMessage());
        }
    }

    private static void printBalances(InMemoryAccountRepository repository, Long... ids) {
        for (Long id : ids) {
            repository.findById(id).ifPresent(acc ->
                System.out.println(acc.getOwnerName() + ": " + acc.getBalance()));
        }
    }
}
