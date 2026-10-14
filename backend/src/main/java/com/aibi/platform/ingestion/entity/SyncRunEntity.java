package com.aibi.platform.ingestion.entity;

import java.time.Instant;

public record SyncRunEntity(long id, long tenantId, long syncJobId, int attemptNo,
        String requestKey, String status, String leaseOwner, Instant leaseUntil,
        long fencingToken, String errorCode, String errorSummary, int retryCount,
        boolean deadLetter, Long batchId, Instant startedAt, Instant finishedAt,
        Instant createdAt) {
}
