package br.ufrn.dpb.server.repository;

import br.ufrn.dpb.server.model.Merchant;
import java.util.Optional;

public interface MerchantRepository {
    long count();

    Optional<Merchant> findByMid(String mid);
    Optional<Merchant> findByCnpj(String cnpj);

    Merchant save(Merchant merchant);
}
