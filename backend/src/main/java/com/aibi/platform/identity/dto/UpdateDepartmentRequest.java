package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @Positive @JsonResourceId Long parentId,
        @NotBlank @Size(max = 200) String departmentName,
        @PositiveOrZero int sortOrder,
        @PositiveOrZero long lockVersion) {
}
