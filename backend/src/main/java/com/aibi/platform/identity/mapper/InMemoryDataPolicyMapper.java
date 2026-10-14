package com.aibi.platform.identity.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.identity.entity.ColumnPolicyEntity;
import com.aibi.platform.identity.entity.PolicyGrantEntity;
import com.aibi.platform.identity.entity.RowPolicyEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryDataPolicyMapper implements DataPolicyMapper {

    private final ConcurrentMap<Long, RowPolicyEntity> rowPolicies = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> rowPolicyIdsByCode = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PolicyGrantEntity> grants = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ColumnPolicyEntity> columnPolicies = new ConcurrentHashMap<>();

    @Override
    public RowPolicyEntity insertRowPolicy(RowPolicyEntity policy) {
        String key = policy.tenantId() + ":" + policy.policyCode();
        if (rowPolicyIdsByCode.putIfAbsent(key, policy.id()) != null
                || rowPolicies.putIfAbsent(policy.id(), policy) != null) {
            rowPolicyIdsByCode.remove(key, policy.id());
            throw new ConflictException("行级策略编码已存在");
        }
        return policy;
    }

    @Override
    public Optional<RowPolicyEntity> findRowPolicy(long tenantId, long policyId) {
        return Optional.ofNullable(rowPolicies.get(policyId))
                .filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public List<RowPolicyEntity> findRowPolicies(long tenantId, long datasetId) {
        return rowPolicies.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.datasetId() == datasetId)
                .sorted(Comparator.comparing(RowPolicyEntity::policyGroup)
                        .thenComparing(RowPolicyEntity::policyCode)).toList();
    }

    @Override
    public PolicyGrantEntity insertGrant(PolicyGrantEntity grant) {
        String key = grantKey(grant.tenantId(), grant.policyId(),
                grant.principalType(), grant.principalId());
        if (grants.putIfAbsent(key, grant) != null) {
            throw new ConflictException("策略已授予该主体");
        }
        return grant;
    }

    @Override
    public List<PolicyGrantEntity> findGrants(long tenantId, long policyId) {
        return grants.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.policyId() == policyId)
                .sorted(Comparator.comparingLong(PolicyGrantEntity::id)).toList();
    }

    @Override
    public ColumnPolicyEntity setColumnPolicy(ColumnPolicyEntity policy) {
        columnPolicies.put(columnKey(
                policy.tenantId(), policy.datasetFieldId(),
                policy.principalType(), policy.principalId()), policy);
        return policy;
    }

    @Override
    public List<ColumnPolicyEntity> findColumnPolicies(long tenantId, List<Long> datasetFieldIds) {
        return columnPolicies.values().stream()
                .filter(value -> value.tenantId() == tenantId
                        && datasetFieldIds.contains(value.datasetFieldId()))
                .sorted(Comparator.comparingLong(ColumnPolicyEntity::datasetFieldId)
                        .thenComparingLong(ColumnPolicyEntity::id)).toList();
    }

    private String grantKey(long tenantId, long policyId, String type, long principalId) {
        return tenantId + ":" + policyId + ":" + type + ":" + principalId;
    }

    private String columnKey(long tenantId, long fieldId, String type, long principalId) {
        return tenantId + ":" + fieldId + ":" + type + ":" + principalId;
    }
}
