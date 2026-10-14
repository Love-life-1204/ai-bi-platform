package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @Positive @JsonResourceId Long workspaceId,
        @Positive @JsonResourceId Long roleId,
        @Min(1) @Max(168) int expiresInHours) {
}
