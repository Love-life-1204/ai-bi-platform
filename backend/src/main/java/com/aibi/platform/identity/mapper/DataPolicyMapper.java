package com.aibi.platform.identity.mapper;

import com.aibi.platform.identity.entity.ColumnPolicyEntity;
import com.aibi.platform.identity.entity.PolicyGrantEntity;
import com.aibi.platform.identity.entity.RowPolicyEntity;
import java.util.List;
import java.util.Optional;

public interface DataPolicyMapper {

    RowPolicyEntity insertRowPolicy(RowPolicyEntity policy);

    Optional<RowPolicyEntity> findRowPolicy(long tenantId, long policyId);

    List<RowPolicyEntity> findRowPolicies(long tenantId, long datasetId);

    PolicyGrantEntity insertGrant(PolicyGrantEntity grant);

    List<PolicyGrantEntity> findGrants(long tenantId, long policyId);

    ColumnPolicyEntity setColumnPolicy(ColumnPolicyEntity policy);

    List<ColumnPolicyEntity> findColumnPolicies(long tenantId, List<Long> datasetFieldIds);
}
