package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.transaction.OptimisticLockExecutor;
import com.aibi.platform.identity.entity.DepartmentEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcDepartmentMapper implements DepartmentMapper {

    private static final String COLUMNS = """
            id, tenant_id, parent_id, department_code, department_name,
            sort_order, created_at, updated_at, lock_version
            """;
    private static final String INSERT_SQL = """
            INSERT INTO sys_department
                (id, tenant_id, parent_id, department_code, department_name,
                 sort_order, created_at, updated_at, lock_version)
            VALUES
                (:id, :tenantId, :parentId, :departmentCode, :departmentName,
                 :sortOrder, :createdAt, :updatedAt, :lockVersion)
            """;
    private static final String UPDATE_SQL = """
            UPDATE sys_department
               SET parent_id=:parentId, department_name=:departmentName,
                   sort_order=:sortOrder, updated_at=:updatedAt, lock_version=:lockVersion
             WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expectedLockVersion
               AND deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OptimisticLockExecutor optimisticLockExecutor;

    public JdbcDepartmentMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            OptimisticLockExecutor optimisticLockExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.optimisticLockExecutor = optimisticLockExecutor;
    }

    @Override
    public DepartmentEntity insert(DepartmentEntity department) {
        try {
            jdbcTemplate.update(INSERT_SQL, parameters(department));
            return department;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("部门编码已存在");
        }
    }

    @Override
    public Optional<DepartmentEntity> findById(long tenantId, long departmentId) {
        return query("SELECT " + COLUMNS + " FROM sys_department"
                        + " WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL",
                Map.of("tenantId", tenantId, "id", departmentId)).stream().findFirst();
    }

    @Override
    public List<DepartmentEntity> findByTenantId(long tenantId) {
        return query("SELECT " + COLUMNS + " FROM sys_department"
                        + " WHERE tenant_id=:tenantId AND deleted_at IS NULL"
                        + " ORDER BY sort_order, id",
                Map.of("tenantId", tenantId));
    }

    @Override
    public DepartmentEntity update(DepartmentEntity department, long expectedLockVersion) {
        Map<String, Object> parameters = new HashMap<>(parameters(department).getValues());
        parameters.put("expectedLockVersion", expectedLockVersion);
        optimisticLockExecutor.updateExactlyOne(UPDATE_SQL, parameters);
        return department;
    }

    private MapSqlParameterSource parameters(DepartmentEntity department) {
        return new MapSqlParameterSource()
                .addValue("id", department.id()).addValue("tenantId", department.tenantId())
                .addValue("parentId", department.parentId())
                .addValue("departmentCode", department.departmentCode())
                .addValue("departmentName", department.departmentName())
                .addValue("sortOrder", department.sortOrder())
                .addValue("createdAt", Timestamp.from(department.createdAt()))
                .addValue("updatedAt", Timestamp.from(department.updatedAt()))
                .addValue("lockVersion", department.lockVersion());
    }

    private List<DepartmentEntity> query(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, this::mapDepartment);
    }

    private DepartmentEntity mapDepartment(ResultSet resultSet, int rowNumber) throws SQLException {
        long parentId = resultSet.getLong("parent_id");
        Long nullableParent = resultSet.wasNull() ? null : parentId;
        return new DepartmentEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"), nullableParent,
                resultSet.getString("department_code"), resultSet.getString("department_name"),
                resultSet.getInt("sort_order"), resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(), resultSet.getLong("lock_version"));
    }
}
