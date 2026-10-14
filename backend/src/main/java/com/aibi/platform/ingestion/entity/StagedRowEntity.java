package com.aibi.platform.ingestion.entity;

import com.aibi.platform.ingestion.dto.IngestionDtos.IdempotentEventKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record StagedRowEntity(IdempotentEventKey eventKey, long batchId,
        long fencingToken, Map<String, Object> values, BigDecimal amount,
        Instant updatedAt, boolean deleted) {

    public StagedRowEntity {
        values = Map.copyOf(values);
    }
}
