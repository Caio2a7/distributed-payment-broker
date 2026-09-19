package br.ufrn.dpb.ledger.repository;

import br.ufrn.dpb.ledger.model.Merchant;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class InMemoryMerchantRepository implements MerchantRepository {
    private final ConcurrentHashMap<String, Merchant> merchants = new ConcurrentHashMap<>(); 
    private static final Logger LOGGER = Logger.getLogger(InMemoryMerchantRepository.class.getName());

    @Override
    public long count() {
        return merchants.mappingCount(); 
    } 

    @Override
    public Optional<Merchant> findByMid(String mid) {
        if (mid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(merchants.get(mid));
    }
    
    @Override
    public Optional<Merchant> findByCnpj(String cnpj) {
        if (cnpj == null) {
            return Optional.empty();
        }
        return merchants.values().stream()
            .filter(m -> m.getCnpj().equals(cnpj))
            .findFirst();
    }

    @Override
    public Merchant save(Merchant merchant) {
        merchants.put(merchant.getMid(), merchant);
        LOGGER.info(() -> "Merchant saved [mid=" + merchant.getMid() + "]");
        return merchant;
    }
}
