package com.aibi.platform.ingestion.entity;

import java.time.Instant;

public record QualityRuleEntity(long id, long tenantId, long datasetId,
        String ruleCode, String ruleType, String field, String expected,
        String minimum, String maximum, String severity, String status,
        Instant createdAt) {
}
