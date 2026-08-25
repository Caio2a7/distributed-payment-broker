package br.ufrn.dpb.server.repository;

import br.ufrn.dpb.server.model.Account;
import java.util.Optional;
import java.math.BigDecimal;

public interface AccountRepository {
    Long nextId(); 

    boolean existsById(Long id);

    Optional<Account> findById(Long id);

    void save(Account account);
    Account create(BigDecimal balance);
}
