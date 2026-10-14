package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import java.time.Instant;

public record MemberData(
        @JsonResourceId long id,
        @JsonResourceId long tenantId,
        @JsonResourceId long userId,
        String memberName,
        String memberStatus,
        boolean owner,
        long permissionVersion,
        Instant joinedAt,
        long lockVersion) {
}
