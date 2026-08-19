package br.ufrn.dpb.server.model;

import java.math.BigDecimal;
import java.time.Instant;

public class Transaction {
    private final Long id;
    private final Instant createdAt;
    private final Long sourceAccountId;
    private final Long destAccountId;
    private final BigDecimal amount;
    private TransactionStatus status;

    public Transaction(Long id, Long sourceAccountId, Long destAccountId, BigDecimal amount, TransactionStatus status) {
        this.id = id;
        this.createdAt = Instant.now();
        this.sourceAccountId = sourceAccountId;
        this.destAccountId = destAccountId;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public Long getDestAccountId() {
        return destAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
}
