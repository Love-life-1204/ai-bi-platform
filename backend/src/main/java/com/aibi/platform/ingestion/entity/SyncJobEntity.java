package com.aibi.platform.ingestion.entity;

import com.aibi.platform.ingestion.dto.IngestionDtos.FieldMapping;
import java.time.Instant;
import java.util.List;

public record SyncJobEntity(long id, long tenantId, long datasourceId, long datasetId,
        String jobCode, String syncMode, String sourceSchema, String sourceTable,
        String primaryKeyField, String updatedAtField, String amountField,
        List<FieldMapping> mappings, int batchSize, String scheduleExpression,
        String timezone, String status, Instant watermarkTime, String watermarkPrimaryKey,
        long lockVersion, Instant createdAt, Instant updatedAt) {

    public SyncJobEntity {
        mappings = List.copyOf(mappings);
    }
}
