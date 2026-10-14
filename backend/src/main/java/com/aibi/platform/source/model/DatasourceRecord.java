package com.aibi.platform.source.model;

import java.time.Instant;

public record DatasourceRecord(
        long id,
        long tenantId,
        long workspaceId,
        String code,
        String name,
        DatasourceType type,
        DatasourceEndpoint endpoint,
        String credentialRef,
        Long ownerMemberId,
        String lastTestStatus,
        Instant lastTestAt,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long lockVersion) {
}
