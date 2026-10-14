package com.aibi.platform.ingestion.entity;

import java.math.BigDecimal;
import java.time.Instant;

public record BatchEntity(long id, long tenantId, long datasetId, Long syncRunId,
        Long importJobId, String batchCode, long generationId, long publishSeq,
        String status, long expectedRows, long receivedRows, long validRows,
        long rejectedRows, long deduplicatedRows, BigDecimal sourceAmount,
        BigDecimal targetAmount, long sourceBusinessKeys, long targetBusinessKeys,
        String qualityStatus, long fencingToken, String errorReport,
        Instant readyAt, Instant publishedAt) {
}
