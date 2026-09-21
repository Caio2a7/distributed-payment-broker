package br.ufrn.dpb.authorizer.repository;

import br.ufrn.dpb.authorizer.model.Transaction;
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
