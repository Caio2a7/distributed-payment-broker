package br.ufrn.dpb.server.service;

import br.ufrn.dpb.server.model.*;
import br.ufrn.dpb.server.repository.*;
import java.math.BigDecimal;

public class PaymentService {
    private final AccountRepository accountRepository;

    public PaymentService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Transaction ProcessTransaction(Long sourceAccountId, Long destAccountId, BigDecimal amount) {
        if (sourceAccountId.equals(destAccountId)) {
            throw new IllegalArgumentException("As chave de destino não pode ser a mesma da chave de origem");
        }

        Account source = accountRepository.findById(sourceAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Melhorar erro"));
        Account destination = accountRepository.findById(destAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Melhorar erro"));

        Long id = accountRepository.nextId();
        Transaction transaction = new Transaction(id, sourceAccountId, destAccountId, amount,
                TransactionStatus.PENDING);

        source.sacar(amount);
        destination.depositar(amount);
        accountRepository.save(source);
        accountRepository.save(destination);

        transaction.setStatus(TransactionStatus.COMPLETED);

        return transaction;
    }


    public static void main(String[] args) {
        System.out.println("EAE");

    }
}
