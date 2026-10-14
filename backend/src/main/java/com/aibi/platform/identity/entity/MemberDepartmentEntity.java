package com.aibi.platform.identity.entity;

public record MemberDepartmentEntity(
        long id,
        long tenantId,
        long memberId,
        long departmentId,
        boolean primary) {
}
