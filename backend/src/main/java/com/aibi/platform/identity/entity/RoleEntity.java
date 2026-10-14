package com.aibi.platform.identity.entity;

public record RoleEntity(
        long id,
        long tenantId,
        String roleCode,
        String roleName,
        String roleStatus,
        boolean builtin,
        long lockVersion) {
}
