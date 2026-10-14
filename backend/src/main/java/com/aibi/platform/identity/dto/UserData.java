package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import java.time.Instant;

public record UserData(
        @JsonResourceId long id,
        String email,
        String displayName,
        String userStatus,
        Instant createdAt) {
}
