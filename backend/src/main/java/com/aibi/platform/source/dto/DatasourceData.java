package com.aibi.platform.source.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.source.model.DatasourceEndpoint;
import com.aibi.platform.source.model.DatasourceType;
import java.time.Instant;

public record DatasourceData(
        @JsonResourceId long id,
        @JsonResourceId long workspaceId,
        String code,
        String name,
        DatasourceType type,
        DatasourceEndpoint endpoint,
        boolean hasCredential,
        @JsonResourceId Long ownerMemberId,
        String lastTestStatus,
        Instant lastTestAt,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long lockVersion) {
}
