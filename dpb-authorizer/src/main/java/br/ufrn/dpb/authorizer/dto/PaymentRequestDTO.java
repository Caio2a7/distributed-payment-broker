package br.ufrn.dpb.authorizer.dto;

import br.ufrn.dpb.authorizer.model.Transaction;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record PaymentRequestDTO(
    String mid,
    Transaction.Operation operation,
    String cardNumber,
    int expMonth,
    int expYear,
    long grossAmount,
    long installments
) {
    public PaymentRequestDTO {
        Objects.requireNonNull(mid, "mid cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(cardNumber, "cardNumber cannot be null");
    }

    public byte[] toBytes() {
        byte[] midBytes = mid.getBytes(StandardCharsets.UTF_8);
        byte[] cardBytes = cardNumber.getBytes(StandardCharsets.UTF_8);

        int totalSize = 1 + midBytes.length + 1 + 1 + cardBytes.length + Integer.BYTES + Integer.BYTES + Long.BYTES + Long.BYTES;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.put((byte) midBytes.length);
        buffer.put(midBytes);
        buffer.put((byte) operation.ordinal());
        buffer.put((byte) cardBytes.length);
        buffer.put(cardBytes);
        buffer.putInt(expMonth);
        buffer.putInt(expYear);
        buffer.putLong(grossAmount);
        buffer.putLong(installments);

        return buffer.array();
    }

    public static PaymentRequestDTO fromBytes(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Payload cannot be null or empty");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        int midLength = Byte.toUnsignedInt(buffer.get());
        byte[] midBytes = new byte[midLength];
        buffer.get(midBytes);
        String mid = new String(midBytes, StandardCharsets.UTF_8);

        Transaction.Operation operation = Transaction.Operation.fromCode(buffer.get());

        int cardLength = Byte.toUnsignedInt(buffer.get());
        byte[] cardBytes = new byte[cardLength];
        buffer.get(cardBytes);
        String cardNumber = new String(cardBytes, StandardCharsets.UTF_8);

        int expMonth = buffer.getInt();
        int expYear = buffer.getInt();
        long grossAmount = buffer.getLong();
        long installments = buffer.getLong();

        return new PaymentRequestDTO(mid, operation, cardNumber, expMonth, expYear, grossAmount, installments);
    }
}
