package br.ufrn.dpb.server.model;

import java.time.Instant;

public class Transaction {
    public enum Status {
        PENDING,
        REJECTED,
        ACCEPTED,
        COMPLETED,
        FAILED;

        public boolean isFinished() {
            return this == REJECTED || this == COMPLETED || this == FAILED;
        }
    }
    public enum Operation {
        PAYMENT,
        REFUND
    }

    private final String fingerprint;
    private final Operation operation;
    private final String mid;
    private final CreditCard creditCard;
    private final long grossAmount;
    private final long feeAmount;
    private final long liquidAmount;
    private final long installments;
    private volatile Status status;
    private final Instant createdAt;


    public Transaction(String fingerprint, Operation operation, String mid, CreditCard creditCard, long grossAmount, long feeAmount, long liquidAmount, long installments) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("Invalid fingerprint");
        }
        if (mid == null || !mid.matches("\\d{15}")) {
            throw new IllegalArgumentException("MID must contain exactly 15 numeric digits");
        }
        if (creditCard == null) {
            throw new IllegalArgumentException("CreditCard cannot be null");
        }
        if (grossAmount <= 0) {
            throw new IllegalArgumentException("Gross amount must be greater than zero");
        }
        if (feeAmount < 0) {
            throw new IllegalArgumentException("Fee amount cannot be negative");
        }
        if (liquidAmount < 0 || liquidAmount > grossAmount || liquidAmount != grossAmount - feeAmount) {
            throw new IllegalArgumentException("Invalid liquid amount");
        }
        if (installments <= 0) {
            throw new IllegalArgumentException("Installments must be greater than zero");
        }
        this.fingerprint = fingerprint;
        this.mid = mid;
        this.creditCard = creditCard;
        this.grossAmount = grossAmount;
        this.feeAmount = feeAmount;
        this.liquidAmount = liquidAmount;
        this.installments = installments;
        this.operation = operation;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void setStatus(Status status) {
        if (this.status.isFinished()) {
            throw new IllegalStateException(
                    "Transaction " + fingerprint + " is already finished with status: " + this.status);
        }
        this.status = status;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getMid() {
        return mid;
    }

    public long getGrossAmount() {
        return grossAmount;
    }

    public long getFeeAmount() {
        return feeAmount;
    }

    public long getLiquidAmount() {
        return liquidAmount;
    }

    public long getInstallments() {
        return installments;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Operation getOperation() {
        return operation;
    }

    public CreditCard getCreditCard(){
        return creditCard;
    }
}
