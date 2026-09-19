package br.ufrn.dpb.authorizer.dto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record LedgerOperationDTO(
    String mid,
    byte operationCode,
    long amount
) {
    public LedgerOperationDTO {
        Objects.requireNonNull(mid, "mid cannot be null");
    }

    public byte[] toBytes() {
        byte[] midBytes = mid.getBytes(StandardCharsets.UTF_8);
        int totalSize = 1 + midBytes.length + 1 + Long.BYTES;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.put((byte) midBytes.length);
        buffer.put(midBytes);
        buffer.put(operationCode);
        buffer.putLong(amount);
        return buffer.array();
    }

    public static LedgerOperationDTO fromBytes(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Payload cannot be null or empty");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int midLength = Byte.toUnsignedInt(buffer.get());
        byte[] midBytes = new byte[midLength];
        buffer.get(midBytes);
        String mid = new String(midBytes, StandardCharsets.UTF_8);
        byte operationCode = buffer.get();
        long amount = buffer.getLong();
        return new LedgerOperationDTO(mid, operationCode, amount);
    }
}
