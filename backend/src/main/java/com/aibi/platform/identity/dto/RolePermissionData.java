package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
public record RolePermissionData(
        @JsonResourceId long roleId,
        @JsonResourceId long permissionId,
        String permissionCode,
        String effect) {
}
