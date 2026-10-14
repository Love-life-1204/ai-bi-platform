package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
public record RoleData(
        @JsonResourceId long id,
        @JsonResourceId long tenantId,
        String roleCode,
        String roleName,
        String roleStatus,
        boolean builtin,
        long lockVersion) {
}
