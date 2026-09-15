package br.ufrn.dpb.server.service;

import br.ufrn.dpb.server.model.*;
import br.ufrn.dpb.server.model.CreditCard.CreditCardBrand;
import br.ufrn.dpb.server.repository.*;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class PaymentService {
    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;

    private final SecretKeySpec keySpec;

    public PaymentService(MerchantRepository merchantRepository, TransactionRepository transactionRepository, String secret) {
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
        this.keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
    
    public String calculateFingerprint(String mid, String cardNumber, long amount, long installments) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(this.keySpec);
            byte[] hash = mac.doFinal((mid + ":" + cardNumber + ":" + amount + ":" + installments).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e){
            throw new RuntimeException("Error to calculate transaction fingerprint: ", e);        
        }
    }

    public long calculateFee(CreditCardBrand brand, long grossAmount, long installments) {
        // valores em porcentagem (%)
        long basePercentage = switch (brand) {
            case VISA, MASTERCARD -> 2;
            case ELO, HIPERCARD   -> 3;
            case AMEX             -> 4;
        };

        long totalPercentage = basePercentage + (installments > 1 ? (installments - 1) : 0);

        return grossAmount*totalPercentage / 100;
    }

    public Transaction processPayment(String mid, Transaction.Operation operation, String cardNumber, int expMonth, int expYear, long grossAmount, long installments) {
        Merchant merchant = merchantRepository.findByMid(mid)
        .orElseThrow(() -> new IllegalArgumentException("Merchant with mid " + mid + " not found"));

        CreditCard creditCard = new CreditCard(cardNumber, expMonth, expYear);

        String fingerprint = calculateFingerprint(mid, cardNumber, grossAmount, installments);
        if (transactionRepository.findByFingerprint(fingerprint).isPresent()){
            throw new IllegalArgumentException("Transaction with fingerprint " + fingerprint + " already processed");
        }
        
        long feeAmount = calculateFee(creditCard.getBrand(), grossAmount, installments);
        long liquidAmount = grossAmount - feeAmount;
        Transaction transaction = new Transaction(fingerprint, operation, mid, creditCard, grossAmount, feeAmount, liquidAmount, installments);
        transactionRepository.save(transaction);

        try{
            switch (operation) {
                case PAYMENT:
                    merchant.credit(liquidAmount);
                break;
                case REFUND:
                    merchant.refund(liquidAmount);
                break;
            }
            transaction.setStatus(Transaction.Status.COMPLETED);
        } catch(IllegalArgumentException e){
            transaction.setStatus(Transaction.Status.REJECTED);
        } catch(Exception e){
            transaction.setStatus(Transaction.Status.FAILED);
        } finally{
           transactionRepository.save(transaction); 
        }
        return transaction;
    }
}
