package com.aibi.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(
        @NotBlank @Size(max = 200) String workspaceName,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String workspaceStatus,
        @PositiveOrZero long lockVersion) {
}
