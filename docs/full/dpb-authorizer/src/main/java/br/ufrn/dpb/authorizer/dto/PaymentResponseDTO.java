package br.ufrn.dpb.authorizer.dto;

import br.ufrn.dpb.authorizer.model.Transaction;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record PaymentResponseDTO(
    String fingerprint,
    Transaction.Status status,
    long liquidAmount
) {
    public PaymentResponseDTO {
        Objects.requireNonNull(fingerprint, "fingerprint cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }

    public byte[] toBytes() {
        byte[] fpBytes = fingerprint.getBytes(StandardCharsets.UTF_8);
        int totalSize = 1 + fpBytes.length + 1 + Long.BYTES;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.put((byte) fpBytes.length);
        buffer.put(fpBytes);
        buffer.put(status.code);
        buffer.putLong(liquidAmount);

        return buffer.array();
    }

    public static PaymentResponseDTO fromBytes(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Payload cannot be null or empty");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        int fpLength = Byte.toUnsignedInt(buffer.get());
        byte[] fpBytes = new byte[fpLength];
        buffer.get(fpBytes);
        String fingerprint = new String(fpBytes, StandardCharsets.UTF_8);

        Transaction.Status status = Transaction.Status.fromCode(buffer.get());
        long liquidAmount = buffer.getLong();

        return new PaymentResponseDTO(fingerprint, status, liquidAmount);
    }
}
