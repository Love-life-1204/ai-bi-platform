package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.identity.entity.MemberDepartmentEntity;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcMemberDepartmentMapper implements MemberDepartmentMapper {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMemberDepartmentMapper(
            @Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MemberDepartmentEntity insert(MemberDepartmentEntity value) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_member_department
                        (id, tenant_id, member_id, department_id, is_primary)
                    VALUES (:id, :tenantId, :memberId, :departmentId, :primary)
                    """, Map.of(
                    "id", value.id(), "tenantId", value.tenantId(),
                    "memberId", value.memberId(), "departmentId", value.departmentId(),
                    "primary", value.primary()));
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("成员已加入该部门");
        }
    }

    @Override
    public boolean remove(long tenantId, long departmentId, long memberId) {
        return jdbcTemplate.update("""
                UPDATE sys_member_department
                   SET deleted_at=UTC_TIMESTAMP(3), updated_at=UTC_TIMESTAMP(3),
                       lock_version=lock_version+1
                 WHERE tenant_id=:tenantId AND department_id=:departmentId
                   AND member_id=:memberId AND deleted_at IS NULL
                """, Map.of(
                "tenantId", tenantId, "departmentId", departmentId, "memberId", memberId)) == 1;
    }

    @Override
    public Set<Long> findDepartmentIds(long tenantId, long memberId) {
        return jdbcTemplate.queryForList("""
                SELECT department_id
                  FROM sys_member_department
                 WHERE tenant_id=:tenantId AND member_id=:memberId AND deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "memberId", memberId), Long.class).stream()
                .collect(Collectors.toSet());
    }
}
