package br.ufrn.dpb.ledger.repository;

import br.ufrn.dpb.ledger.model.Merchant;
import java.util.Optional;

public interface MerchantRepository {
    long count();
    Optional<Merchant> findByMid(String mid);
    Optional<Merchant> findByCnpj(String cnpj);
    Merchant save(Merchant merchant);
}
