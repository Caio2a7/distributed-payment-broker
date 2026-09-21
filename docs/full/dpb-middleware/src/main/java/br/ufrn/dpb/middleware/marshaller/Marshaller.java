package br.ufrn.dpb.middleware.marshaller;

import br.ufrn.dpb.middleware.protocol.Message;

import java.util.Objects;
import java.nio.ByteBuffer;

public class Marshaller { 
    public byte[] marshall(Message message) {
        Objects.requireNonNull(message);
        ByteBuffer buffer = ByteBuffer.allocate(Message.HEADER_SIZE + message.getPayloadLength());
        buffer.put(message.getType().code);
        buffer.putInt(message.getRequestId());
        buffer.putInt(message.getPayloadLength());
        buffer.put(message.getPayload());
        return buffer.array();
    }

    public Message unmarshall(byte[] data, int length) {
        if (length < Message.HEADER_SIZE || data == null) {
            throw new IllegalArgumentException("Tamanho inferior ao de cabeçalho: " + length + " bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
        byte typeCode = buffer.get();
        int requestId = buffer.getInt();
        int payloadLength = buffer.getInt();

        if (payloadLength < 0 || buffer.remaining() < payloadLength) {
            throw new IllegalArgumentException("Invalid payload");
        }

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        return new Message(Message.Type.from(typeCode), requestId, payload);
    }
}
