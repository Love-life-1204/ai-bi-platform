package com.aibi.platform.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
        @NotBlank @Size(max = 200) String roleName,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "ACTIVE|DISABLED") String roleStatus,
        @PositiveOrZero long lockVersion) {
}
