package br.ufrn.dpb.server.dto;

import br.ufrn.dpb.server.model.Transaction;

import java.nio.ByteBuffer;

public record PaymentRequestDTO (
    String mid,
    Transaction.Operation operation,
    String cardNumber,
    int expMonth,
    int expYear,
    long grossAmount,
    long installments
    ) {

    public PaymentRequestDTO  fromBytes(byte[] data){
        int length = data.length();
        ByteBuffer buffer = new ByteBuffer(wrap, data, 0, length);
    }   


}
