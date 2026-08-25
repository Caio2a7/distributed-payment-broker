package br.ufrn.dpb.server.dto;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Objects;

public record TransferRequestDTO(Long sourceId, Long destId, BigDecimal amount) {
    public static final int MIN_PAYLOAD_SIZE = (Long.BYTES * 2) + Integer.BYTES + Short.BYTES;

    public TransferRequestDTO {
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(destId);
        Objects.requireNonNull(amount);
    }

    public byte[] toBytes() {
        byte[] amountBytes = amount.unscaledValue().toByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(MIN_PAYLOAD_SIZE + amountBytes.length);
        buffer.putLong(sourceId);
        buffer.putLong(destId);
        putBigDecimal(buffer, amount, amountBytes);
        return buffer.array();
    }

    public static TransferRequestDTO fromBytes(byte[] payload) {
        if (payload == null || payload.length < MIN_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Payload menor que o tamanho minimo: " + (payload == null ? 0 : payload.length));
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        Long sourceId = buffer.getLong();
        Long destId = buffer.getLong();
        BigDecimal amount = getBigDecimal(buffer);

        return new TransferRequestDTO(sourceId, destId, amount);
    }

    private static void putBigDecimal(ByteBuffer buffer, BigDecimal value, byte[] unscaledBytes) {
        buffer.putInt(value.scale());
        buffer.putShort((short) unscaledBytes.length);
        buffer.put(unscaledBytes);
    }

    private static BigDecimal getBigDecimal(ByteBuffer buffer) {
        int scale = buffer.getInt();
        short length = buffer.getShort();

        if (length <= 0 || buffer.remaining() < length) {
            throw new IllegalArgumentException("Tamanho do BigDecimal invalido: " + length);
        }

        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new BigDecimal(new BigInteger(bytes), scale);
    }
}
