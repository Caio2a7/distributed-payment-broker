package br.ufrn.dpb.server.repository;

import br.ufrn.dpb.server.model.Transaction;
import java.util.Optional;
import java.util.List;

public interface TransactionRepository {
    long count();
    long countByStatus(Transaction.Status status);

    Optional<Transaction> findByFingerprint(String fingerprint);
    List<Transaction> findByMid(String mid);

    void updateStatus(String fingerprint, Transaction.Status status); 
    Transaction save(Transaction transaction);
}
