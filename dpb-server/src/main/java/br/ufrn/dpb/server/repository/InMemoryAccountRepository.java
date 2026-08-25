package br.ufrn.dpb.server.repository;

import br.ufrn.dpb.server.model.Account;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryAccountRepository implements AccountRepository {
    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong(0);
    
    @Override
    public Long nextId() {
        return sequence.incrementAndGet();
    }

    @Override
    public boolean existsById(Long accountId) {
        return accounts.containsKey(accountId);
    }

    @Override
    public Optional<Account> findById(Long accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public void save(Account account) {
        accounts.put(account.getId(), account);
    }

    @Override
    public Account create(BigDecimal balance){
        Long id = nextId();
        Account account = new Account(id, balance);
        accounts.put(id, account);
        return account;
    }
}
