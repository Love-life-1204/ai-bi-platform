package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.entity.DepartmentEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryDepartmentMapper implements DepartmentMapper {

    private final ConcurrentMap<Long, DepartmentEntity> departments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> idsByCode = new ConcurrentHashMap<>();

    @Override
    public DepartmentEntity insert(DepartmentEntity department) {
        String key = codeKey(department.tenantId(), department.departmentCode());
        if (idsByCode.putIfAbsent(key, department.id()) != null
                || departments.putIfAbsent(department.id(), department) != null) {
            idsByCode.remove(key, department.id());
            throw new ConflictException("部门编码已存在");
        }
        return department;
    }

    @Override
    public Optional<DepartmentEntity> findById(long tenantId, long departmentId) {
        return Optional.ofNullable(departments.get(departmentId))
                .filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public List<DepartmentEntity> findByTenantId(long tenantId) {
        return departments.values().stream()
                .filter(value -> value.tenantId() == tenantId)
                .sorted(Comparator.comparingInt(DepartmentEntity::sortOrder)
                        .thenComparingLong(DepartmentEntity::id))
                .toList();
    }

    @Override
    public DepartmentEntity update(DepartmentEntity department, long expectedLockVersion) {
        return departments.compute(department.id(), (id, current) -> {
            if (current == null || current.tenantId() != department.tenantId()) {
                throw new NotFoundException("部门不存在");
            }
            if (current.lockVersion() != expectedLockVersion) {
                throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
            }
            return department;
        });
    }

    private String codeKey(long tenantId, String code) {
        return tenantId + ":" + code;
    }
}
