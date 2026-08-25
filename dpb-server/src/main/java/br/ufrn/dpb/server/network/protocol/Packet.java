package br.ufrn.dpb.server.network.protocol;

import java.util.Objects;
import java.nio.ByteBuffer;

public class Packet {
    public static final int HEADER_SIZE = 13;
    private final MessageType type;
    private final Long requestId;
    private final int payloadLength;

    private final byte[] payload;


    public Packet(MessageType type, Long requestId, byte[] payload) {
        this.type = Objects.requireNonNull(type);
        this.requestId = requestId;
        this.payload = (payload != null) ? payload : new byte[0];
        this.payloadLength = this.payload.length;
    }

    public MessageType getType() { return type; }
    public Long getRequestId() { return requestId; }
    public int getPayloadLength() { return payloadLength; }
    public byte[] getPayload() { return payload; }

    public byte[] toBytes(){
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payloadLength);
        buffer.put(type.code);
        buffer.putLong(requestId);
        buffer.putInt(payloadLength);
        buffer.put(payload);
        return buffer.array();
    }

    public static Packet fromBytes(byte[] data, int length){
        if(length < HEADER_SIZE){
            throw new IllegalArgumentException("Tamanho inferior ao de cabeçalho: " + length + " bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
        byte typeCode = buffer.get();
        Long requestId = buffer.getLong();
        int payloadLength = buffer.getInt();
        
        if(payloadLength < 0 || buffer.remaining() < payloadLength){
            throw new IllegalArgumentException("Payload de tamanho inválido ou corrompido");
        }

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        return new Packet(MessageType.from(typeCode), requestId, payload);
    }
}
