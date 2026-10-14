package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record GrantPolicyRequest(
        @NotBlank @Pattern(regexp = "MEMBER|ROLE|DEPARTMENT") String principalType,
        @Positive @JsonResourceId long principalId,
        @Future Instant expiresAt) {
}
