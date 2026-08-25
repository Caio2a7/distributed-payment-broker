package br.ufrn.dpb.server.dto;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Objects;

public record TransferResponseDTO(Long transactionId, BigDecimal currentBalance) {
    public static final int MIN_PAYLOAD_SIZE = Long.BYTES + Integer.BYTES + Short.BYTES;

    public TransferResponseDTO {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(currentBalance);
    }

    public byte[] toBytes() {
        byte[] balanceBytes = currentBalance.unscaledValue().toByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(MIN_PAYLOAD_SIZE + balanceBytes.length);
        buffer.putLong(transactionId);
        putBigDecimal(buffer, currentBalance, balanceBytes);
        return buffer.array();
    }

    public static TransferResponseDTO fromBytes(byte[] payload) {
        if (payload == null || payload.length < MIN_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Payload menor que o tamanho minimo: " + (payload == null ? 0 : payload.length));
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        Long transactionId = buffer.getLong();
        BigDecimal currentBalance = getBigDecimal(buffer);

        return new TransferResponseDTO(transactionId, currentBalance);
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
