package br.ufrn.dpb.authorizer.protocol;

import java.util.Objects;

public class Message {
    public enum Type {
        REQUEST(1),
        RESPONSE(2),
        HEARTBEAT(3),
        HEARTBEAT_ACK(4),
        REGISTER(5),
        REGISTER_ACK(6),
        LEDGER_REQUEST(7),
        LEDGER_RESPONSE(8),
        ERROR(99);

        public final byte code;

        Type(int code) {
            this.code = (byte) code;
        }

        public static Type from(byte code) {
            for (Type type : values()) {
                if (type.code == code)
                    return type;
            }
            throw new IllegalArgumentException("Unknown protocol type: " + code);
        }
    };

    public static final int HEADER_SIZE = 9;
    private final Type type;
    private final int requestId;
    private final int payloadLength;
    private final byte[] payload;

    public Message(Type type, int requestId, byte[] payload) {
        this.type = Objects.requireNonNull(type);
        this.requestId = requestId;
        this.payload = (payload != null) ? payload : new byte[0];
        this.payloadLength = this.payload.length;
    }

    public Type getType() { return type; }
    public int getRequestId() { return requestId; }
    public int getPayloadLength() { return payloadLength; }
    public byte[] getPayload() { return payload; }
}
