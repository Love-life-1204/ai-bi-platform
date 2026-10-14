package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.identity.entity.MemberDepartmentEntity;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryMemberDepartmentMapper implements MemberDepartmentMapper {

    private final ConcurrentMap<String, MemberDepartmentEntity> values = new ConcurrentHashMap<>();

    @Override
    public MemberDepartmentEntity insert(MemberDepartmentEntity value) {
        if (values.putIfAbsent(key(
                value.tenantId(), value.departmentId(), value.memberId()), value) != null) {
            throw new ConflictException("成员已加入该部门");
        }
        return value;
    }

    @Override
    public boolean remove(long tenantId, long departmentId, long memberId) {
        return values.remove(key(tenantId, departmentId, memberId)) != null;
    }

    @Override
    public Set<Long> findDepartmentIds(long tenantId, long memberId) {
        return values.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.memberId() == memberId)
                .map(MemberDepartmentEntity::departmentId).collect(Collectors.toSet());
    }

    private String key(long tenantId, long departmentId, long memberId) {
        return tenantId + ":" + departmentId + ":" + memberId;
    }
}
