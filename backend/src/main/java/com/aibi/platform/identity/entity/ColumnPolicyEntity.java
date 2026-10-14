package com.aibi.platform.identity.entity;

public record ColumnPolicyEntity(
        long id,
        long tenantId,
        long datasetFieldId,
        String principalType,
        long principalId,
        String accessMode,
        String maskConfig) {
}
