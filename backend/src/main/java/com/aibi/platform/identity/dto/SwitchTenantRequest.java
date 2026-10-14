package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.constraints.Positive;

public record SwitchTenantRequest(@Positive @JsonResourceId long tenantId) {
}
