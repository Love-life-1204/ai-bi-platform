package com.aibi.platform.common.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record ChangeTenantStatusRequest(
        @PositiveOrZero long lockVersion) {
}
