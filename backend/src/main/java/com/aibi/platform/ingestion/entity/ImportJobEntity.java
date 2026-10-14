package com.aibi.platform.ingestion.entity;

import com.aibi.platform.ingestion.dto.IngestionDtos.FieldMapping;
import java.time.Instant;
import java.util.List;

public record ImportJobEntity(long id, long tenantId, long workspaceId, long datasetId,
        long requesterMemberId, long objectFileId, String requestKey,
        List<FieldMapping> mappings, boolean strictMode, String status,
        long totalRows, long validRows, long errorRows, Long errorFileId,
        String errorSummary, Instant createdAt, Instant updatedAt) {

    public ImportJobEntity {
        mappings = List.copyOf(mappings);
    }
}
