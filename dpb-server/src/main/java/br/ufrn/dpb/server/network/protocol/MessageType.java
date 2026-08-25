package br.ufrn.dpb.server.network.protocol;

public enum MessageType {
    TRANSFER_REQUEST(1),
    TRANSFER_RESPONSE(2),
    ERROR(99);

    public final byte code;

    MessageType(int code){
        this.code = (byte) code;
    }

    public static MessageType from(byte code){
        for(MessageType type : values()){
            if(type.code == code) return type;
        }
        throw new IllegalArgumentException("Tipo desconhecido: " + code);
    }
}
