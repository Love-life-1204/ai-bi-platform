package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
        @Positive @JsonResourceId Long parentId,
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String departmentCode,
        @NotBlank @Size(max = 200) String departmentName,
        @PositiveOrZero int sortOrder) {
}
