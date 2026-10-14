package com.aibi.platform.identity.mapper;

import com.aibi.platform.identity.entity.DepartmentEntity;
import java.util.List;
import java.util.Optional;

public interface DepartmentMapper {

    DepartmentEntity insert(DepartmentEntity department);

    Optional<DepartmentEntity> findById(long tenantId, long departmentId);

    List<DepartmentEntity> findByTenantId(long tenantId);

    DepartmentEntity update(DepartmentEntity department, long expectedLockVersion);
}
