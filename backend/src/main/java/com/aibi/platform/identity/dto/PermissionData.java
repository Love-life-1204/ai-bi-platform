package com.aibi.platform.identity.dto;

import com.aibi.platform.common.json.JsonResourceId;
public record PermissionData(
        @JsonResourceId long id,
        String permissionCode,
        String permissionName,
        String resourceType,
        String actionCode,
        long lockVersion) {
}
