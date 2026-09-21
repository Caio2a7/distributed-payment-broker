package br.ufrn.dpb.ledger.service;

import br.ufrn.dpb.ledger.model.Merchant;
import br.ufrn.dpb.ledger.repository.MerchantRepository;

import java.util.logging.Logger;

public class LedgerService {
    private static final Logger LOGGER = Logger.getLogger(LedgerService.class.getName());
    private final MerchantRepository merchantRepository;

    public LedgerService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public long credit(String mid, long amount) {
        Merchant merchant = merchantRepository.findByMid(mid)
                .orElseThrow(() -> new IllegalArgumentException("Merchant with mid " + mid + " not found"));
        merchant.credit(amount);
        LOGGER.info(() -> "Credit applied: mid=" + mid + ", amount=" + amount + ", newBalance=" + merchant.getBalance());
        return merchant.getBalance();
    }

    public long refund(String mid, long amount) {
        Merchant merchant = merchantRepository.findByMid(mid)
                .orElseThrow(() -> new IllegalArgumentException("Merchant with mid " + mid + " not found"));
        merchant.refund(amount);
        LOGGER.info(() -> "Refund applied: mid=" + mid + ", amount=" + amount + ", newBalance=" + merchant.getBalance());
        return merchant.getBalance();
    }

    public long getBalance(String mid) {
        Merchant merchant = merchantRepository.findByMid(mid)
                .orElseThrow(() -> new IllegalArgumentException("Merchant with mid " + mid + " not found"));
        return merchant.getBalance();
    }
}
