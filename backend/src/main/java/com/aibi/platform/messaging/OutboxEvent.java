package com.aibi.platform.messaging;

public record OutboxEvent(
        long id,
        String eventKey,
        TaskEnvelope envelope,
        int retryCount) {
}
