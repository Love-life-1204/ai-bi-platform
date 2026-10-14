package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.identity.entity.ColumnPolicyEntity;
import com.aibi.platform.identity.entity.PolicyGrantEntity;
import com.aibi.platform.identity.entity.RowPolicyEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
public class JdbcDataPolicyMapper implements DataPolicyMapper {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDataPolicyMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RowPolicyEntity insertRowPolicy(RowPolicyEntity policy) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO sys_data_policy
                        (id, tenant_id, dataset_id, policy_code, policy_name,
                         policy_ast, policy_group, policy_status, policy_version)
                    VALUES
                        (:id, :tenantId, :datasetId, :code, :name,
                         CAST(:ast AS JSON), :groupName, :status, :version)
                    """, Map.of(
                    "id", policy.id(), "tenantId", policy.tenantId(),
                    "datasetId", policy.datasetId(), "code", policy.policyCode(),
                    "name", policy.policyName(), "ast", policy.policyAst(),
                    "groupName", policy.policyGroup(), "status", policy.policyStatus(),
                    "version", policy.policyVersion()));
            return policy;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("行级策略编码已存在");
        }
    }

    @Override
    public Optional<RowPolicyEntity> findRowPolicy(long tenantId, long policyId) {
        return queryRowPolicies("""
                SELECT id, tenant_id, dataset_id, policy_code, policy_name,
                       policy_ast, policy_group, policy_status, policy_version
                  FROM sys_data_policy
                 WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "id", policyId)).stream().findFirst();
    }

    @Override
    public List<RowPolicyEntity> findRowPolicies(long tenantId, long datasetId) {
        return queryRowPolicies("""
                SELECT id, tenant_id, dataset_id, policy_code, policy_name,
                       policy_ast, policy_group, policy_status, policy_version
                  FROM sys_data_policy
                 WHERE tenant_id=:tenantId AND dataset_id=:datasetId AND deleted_at IS NULL
                 ORDER BY policy_group, policy_code
                """, Map.of("tenantId", tenantId, "datasetId", datasetId));
    }

    @Override
    public PolicyGrantEntity insertGrant(PolicyGrantEntity grant) {
        try {
            MapSqlParameterSource parameters = new MapSqlParameterSource()
                    .addValue("id", grant.id()).addValue("tenantId", grant.tenantId())
                    .addValue("policyId", grant.policyId()).addValue("type", grant.principalType())
                    .addValue("principalId", grant.principalId())
                    .addValue("expiresAt", grant.expiresAt() == null
                            ? null : Timestamp.from(grant.expiresAt()));
            jdbcTemplate.update("""
                    INSERT INTO sys_policy_grant
                        (id, tenant_id, policy_id, principal_type, principal_id, expires_at)
                    VALUES (:id, :tenantId, :policyId, :type, :principalId, :expiresAt)
                    """, parameters);
            return grant;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("策略已授予该主体");
        }
    }

    @Override
    public List<PolicyGrantEntity> findGrants(long tenantId, long policyId) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, policy_id, principal_type, principal_id, expires_at
                  FROM sys_policy_grant
                 WHERE tenant_id=:tenantId AND policy_id=:policyId AND deleted_at IS NULL
                 ORDER BY id
                """, Map.of("tenantId", tenantId, "policyId", policyId), this::mapGrant);
    }

    @Override
    public ColumnPolicyEntity setColumnPolicy(ColumnPolicyEntity policy) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", policy.id()).addValue("tenantId", policy.tenantId())
                .addValue("fieldId", policy.datasetFieldId()).addValue("type", policy.principalType())
                .addValue("principalId", policy.principalId()).addValue("mode", policy.accessMode())
                .addValue("maskConfig", policy.maskConfig() == null ? "null" : policy.maskConfig());
        jdbcTemplate.update("""
                INSERT INTO sys_column_policy
                    (id, tenant_id, dataset_field_id, principal_type, principal_id,
                     access_mode, mask_config)
                VALUES
                    (:id, :tenantId, :fieldId, :type, :principalId,
                     :mode, CAST(:maskConfig AS JSON))
                ON DUPLICATE KEY UPDATE access_mode=:mode, mask_config=CAST(:maskConfig AS JSON),
                                        updated_at=UTC_TIMESTAMP(3), lock_version=lock_version+1
                """, parameters);
        return policy;
    }

    @Override
    public List<ColumnPolicyEntity> findColumnPolicies(long tenantId, List<Long> datasetFieldIds) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, dataset_field_id, principal_type, principal_id,
                       access_mode, mask_config
                  FROM sys_column_policy
                 WHERE tenant_id=:tenantId AND dataset_field_id IN (:fieldIds)
                   AND deleted_at IS NULL
                 ORDER BY dataset_field_id, id
                """, Map.of("tenantId", tenantId, "fieldIds", datasetFieldIds), this::mapColumnPolicy);
    }

    private List<RowPolicyEntity> queryRowPolicies(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.query(sql, parameters, this::mapRowPolicy);
    }

    private RowPolicyEntity mapRowPolicy(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RowPolicyEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"),
                resultSet.getLong("dataset_id"), resultSet.getString("policy_code"),
                resultSet.getString("policy_name"), resultSet.getString("policy_ast"),
                resultSet.getString("policy_group"), resultSet.getString("policy_status"),
                resultSet.getLong("policy_version"));
    }

    private PolicyGrantEntity mapGrant(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp expiresAt = resultSet.getTimestamp("expires_at");
        return new PolicyGrantEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"),
                resultSet.getLong("policy_id"), resultSet.getString("principal_type"),
                resultSet.getLong("principal_id"), expiresAt == null ? null : expiresAt.toInstant());
    }

    private ColumnPolicyEntity mapColumnPolicy(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ColumnPolicyEntity(
                resultSet.getLong("id"), resultSet.getLong("tenant_id"),
                resultSet.getLong("dataset_field_id"), resultSet.getString("principal_type"),
                resultSet.getLong("principal_id"), resultSet.getString("access_mode"),
                resultSet.getString("mask_config"));
    }
}
