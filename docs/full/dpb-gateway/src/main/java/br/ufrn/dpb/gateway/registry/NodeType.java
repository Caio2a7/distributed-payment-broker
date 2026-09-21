package br.ufrn.dpb.gateway.registry;

public enum NodeType {
    AUTHORIZER,
    LEDGER;

    public static NodeType fromString(String text) {
        for (NodeType type : values()) {
            if (type.name().equalsIgnoreCase(text.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node type: " + text);
    }
}
