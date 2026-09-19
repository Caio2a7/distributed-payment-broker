package br.ufrn.dpb.authorizer.service;

import br.ufrn.dpb.authorizer.channel.SingleSocketChannel;
import br.ufrn.dpb.authorizer.dto.LedgerOperationDTO;
import br.ufrn.dpb.authorizer.model.CreditCard;
import br.ufrn.dpb.authorizer.model.Transaction;
import br.ufrn.dpb.authorizer.protocol.Message;
import br.ufrn.dpb.authorizer.repository.TransactionRepository;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.locks.ReentrantLock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class PaymentService {
    private final TransactionRepository transactionRepository;
    private final SecretKeySpec keySpec;
    private final String gatewayHost;
    private final int gatewayPort;
    private SingleSocketChannel ledgerChannel;
    private final ReentrantLock ledgerChannelLock = new ReentrantLock();

    public PaymentService(TransactionRepository transactionRepository, String secret, String gatewayHost, int gatewayPort) {
        this.transactionRepository = transactionRepository;
        this.keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.gatewayHost = gatewayHost;
        this.gatewayPort = gatewayPort;
    }

    private SingleSocketChannel getLedgerChannel() throws Exception {
        ledgerChannelLock.lock();
        try {
            if (ledgerChannel == null || !ledgerChannel.isConnected()) {
                ledgerChannel = new SingleSocketChannel(this.gatewayHost, this.gatewayPort);
            }
            return ledgerChannel;
        } finally {
            ledgerChannelLock.unlock();
        }
    }

    public String calculateFingerprint(String mid, String cardNumber, long amount, long installments) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(this.keySpec);
            byte[] hash = mac.doFinal((mid + ":" + cardNumber + ":" + amount + ":" + installments).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error to calculate transaction fingerprint: ", e);
        }
    }

    public long calculateFee(CreditCard.CreditCardBrand brand, long grossAmount, long installments) {
        long basePercentage = switch (brand) {
            case VISA, MASTERCARD -> 2;
            case ELO, HIPERCARD -> 3;
            case AMEX -> 4;
        };
        long totalPercentage = basePercentage + (installments > 1 ? (installments - 1) : 0);
        return grossAmount * totalPercentage / 100;
    }

    public Transaction processPayment(String mid, Transaction.Operation operation, String cardNumber, int expMonth, int expYear, long grossAmount, long installments) {
        CreditCard creditCard = new CreditCard(cardNumber, expMonth, expYear);
        String fingerprint = calculateFingerprint(mid, cardNumber, grossAmount, installments);

        if (transactionRepository.findByFingerprint(fingerprint).isPresent()) {
            throw new IllegalArgumentException("Transaction with fingerprint " + fingerprint + " already processed");
        }

        long feeAmount = calculateFee(creditCard.getBrand(), grossAmount, installments);
        long liquidAmount = grossAmount - feeAmount;

        Transaction transaction = new Transaction(fingerprint, operation, mid, creditCard, grossAmount, feeAmount, liquidAmount, installments);
        transactionRepository.save(transaction);

        try {
            byte opCode = (operation == Transaction.Operation.PAYMENT) ? (byte) 1 : (byte) 2;
            LedgerOperationDTO ledgerOp = new LedgerOperationDTO(mid, opCode, liquidAmount);

            Message resp;
            ledgerChannelLock.lock();
            try {
                SingleSocketChannel gatewayChannel = getLedgerChannel();
                try {
                    Message req = new Message(Message.Type.LEDGER_REQUEST, 0, ledgerOp.toBytes());
                    gatewayChannel.writeMessage(req);
                    resp = gatewayChannel.readMessage();
                } catch (Exception e) {
                    try { gatewayChannel.close(); } catch (Exception ignored) {}
                    ledgerChannel = null;
                    throw e;
                }
            } finally {
                ledgerChannelLock.unlock();
            }

            if (resp.getType() == Message.Type.ERROR) {
                throw new IllegalArgumentException(new String(resp.getPayload(), StandardCharsets.UTF_8));
            }

            transaction.setStatus(Transaction.Status.COMPLETED);
        } catch (IllegalArgumentException e) {
            transaction.setStatus(Transaction.Status.REJECTED);
        } catch (Exception e) {
            transaction.setStatus(Transaction.Status.FAILED);
        } finally {
            transactionRepository.save(transaction);
        }

        return transaction;
    }
}
