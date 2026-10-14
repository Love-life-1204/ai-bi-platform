package com.aibi.platform.messaging;

public enum TaskKind {
    SYNC("sync"),
    IMPORT("import"),
    EXPORT("export"),
    AI("ai");

    private final String code;

    TaskKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public String routingKey() {
        return "task." + code;
    }

    public String queue() {
        return "aibi." + code + ".q";
    }

    public String deadLetterRoutingKey() {
        return "dead." + code;
    }

    public String deadLetterQueue() {
        return "aibi." + code + ".dlq";
    }
}
