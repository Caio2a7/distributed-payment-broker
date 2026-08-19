package br.ufrn.dpb.server.model;

import java.math.BigDecimal;

public class Account {
    private final Long id;
    private BigDecimal balance;

    public Account(Long id, BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Saldo da conta não pode ser negativo");
        }
        this.id = id;
        this.balance = balance;
    }

    public void depositar(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Depósito não pode ser negativo");
        }
        balance = balance.add(amount);
    }

    public void sacar(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Saque não pode ser negativo");
        }
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saque não pode ser mais do que tem na conta");
        }
        balance = balance.subtract(amount);
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

}
