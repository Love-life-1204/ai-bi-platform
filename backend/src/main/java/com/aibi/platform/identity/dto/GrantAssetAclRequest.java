package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record GrantAssetAclRequest(
        @NotBlank @Pattern(regexp = "MEMBER|ROLE|DEPARTMENT") String principalType,
        @Positive @JsonResourceId long principalId,
        @NotBlank @Size(max = 64) String actionCode,
        @NotBlank @Pattern(regexp = "ALLOW|DENY") String effect,
        @Future Instant expiresAt) {
}
