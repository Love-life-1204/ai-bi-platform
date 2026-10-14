package com.aibi.platform.identity.entity;

public record PermissionEntity(
        long id,
        String permissionCode,
        String permissionName,
        String resourceType,
        String actionCode,
        long lockVersion) {
}
