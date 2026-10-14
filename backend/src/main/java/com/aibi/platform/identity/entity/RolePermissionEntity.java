package com.aibi.platform.identity.entity;

public record RolePermissionEntity(
        long id,
        long tenantId,
        long roleId,
        long permissionId,
        String effect) {
}
