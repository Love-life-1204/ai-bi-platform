package com.aibi.platform.messaging;

import java.time.Instant;

public record TaskEnvelope(
        String eventId,
        long tenantId,
        long taskId,
        TaskKind kind,
        int schemaVersion,
        Instant occurredAt,
        Long datasetId) {

    public TaskEnvelope(String eventId, long tenantId, long taskId,
            TaskKind kind, int schemaVersion, Instant occurredAt) {
        this(eventId, tenantId, taskId, kind, schemaVersion, occurredAt, null);
    }
}
