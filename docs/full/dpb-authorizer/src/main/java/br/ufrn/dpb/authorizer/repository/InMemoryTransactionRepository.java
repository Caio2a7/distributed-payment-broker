package br.ufrn.dpb.authorizer.repository;

import br.ufrn.dpb.authorizer.model.Transaction;

import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class InMemoryTransactionRepository implements TransactionRepository {
    private final ConcurrentHashMap<String, Transaction> transactions = new ConcurrentHashMap<>();
    private static final Logger LOGGER = Logger.getLogger(InMemoryTransactionRepository.class.getName());

    @Override
    public long count() {
        return transactions.mappingCount();
    }

    @Override
    public long countByStatus(Transaction.Status status) {
        return transactions.values().stream()
                .filter(t -> t.getStatus() == status)
                .count();
    }

    @Override
    public Optional<Transaction> findByFingerprint(String fingerprint) {
        if (fingerprint == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(transactions.get(fingerprint));
    }

    @Override
    public List<Transaction> findByMid(String mid) {
        return transactions.values().stream()
            .filter(t -> t.getMid().equals(mid))
            .toList();
    }

    @Override
    public Transaction save(Transaction transaction) {
        transactions.put(transaction.getFingerprint(), transaction);
        LOGGER.info(() -> "Transaction saved [fingerprint=" + transaction.getFingerprint() + "]");
        return transaction;
    }

    @Override
    public void updateStatus(String fingerprint, Transaction.Status status) {
        Transaction transaction = findByFingerprint(fingerprint)
                .orElseThrow(() -> new IllegalArgumentException("Transaction does not exist"));
        transaction.setStatus(status);
    }
}
