package com.aibi.platform.identity.entity;

import java.time.Instant;

public record DepartmentEntity(
        long id,
        long tenantId,
        Long parentId,
        String departmentCode,
        String departmentName,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt,
        long lockVersion) {
}
