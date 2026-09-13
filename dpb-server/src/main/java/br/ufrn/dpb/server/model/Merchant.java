package br.ufrn.dpb.server.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class Merchant {
    private final String mid;
    private volatile String tradingName;
    private final String cnpj;
    private final AtomicLong balance;
    private final Instant createdAt;

    public Merchant(String mid, String tradingName, String cnpj, long balance) {
        if (mid == null || !mid.matches("\\d{15}") ) {
            throw new IllegalArgumentException("MID must contain 15 digits");
        }
        if (cnpj == null || cnpj.isBlank()) {
           throw new IllegalArgumentException("Cnpj cannot be null or blank"); 
        }
        if (balance < 0){
           throw new IllegalArgumentException("Initial balance cannot be negative"); 
        }
        if (tradingName == null || tradingName.isBlank()) {
            throw new IllegalArgumentException("Trading name cannot be null or blank");
        }

        this.mid = mid;
        this.tradingName = tradingName;
        this.cnpj = cnpj;
        this.balance = new AtomicLong(balance);
        this.createdAt = Instant.now();
    }

    public void credit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Credit value cannot be negative");
        }
        balance.addAndGet(amount);
    }

    public void refund(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Refund value cannot be negative");
        }
        balance.updateAndGet(current -> {
            if (amount > current) {
                throw new IllegalArgumentException("Refund cannot be debited because merchant has insufficient funds");
            }
            return current - amount;
        });
    }

    public void setTradingName(String tradingName) { 
        if (tradingName == null || tradingName.isBlank()) {
            throw new IllegalArgumentException("Trading name cannot be null or blank");
        }
        this.tradingName = tradingName; 
    }
    
    public String getMid() { return mid; }
    public String getTradingName() { return tradingName; }
    public String getCnpj() { return cnpj; }
    public long getBalance() { return balance.get(); }
    public Instant getCreatedAt() { return createdAt; }
}
