package br.ufrn.dpb.ledger.marshaller;

import br.ufrn.dpb.ledger.protocol.Message;

import java.nio.ByteBuffer;
import java.util.Objects;

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
            throw new IllegalArgumentException("Invalid message length: " + length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
        byte typeCode = buffer.get();
        int requestId = buffer.getInt();
        int payloadLength = buffer.getInt();

        if (payloadLength < 0 || buffer.remaining() < payloadLength) {
            throw new IllegalArgumentException("Invalid payload length");
        }

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        return new Message(Message.Type.from(typeCode), requestId, payload);
    }
}
