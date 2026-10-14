package com.aibi.platform.ingestion.entity;

import java.nio.file.Path;
import java.time.Instant;

public record FileEntity(long id, long tenantId, String fileName, String contentType,
        long sizeBytes, String sha256, Path storagePath, String status, Instant createdAt,
        Long workspaceId, Long ownerMemberId) {
}
