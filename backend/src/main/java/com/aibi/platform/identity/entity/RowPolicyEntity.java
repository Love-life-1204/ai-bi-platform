package com.aibi.platform.identity.entity;

public record RowPolicyEntity(
        long id,
        long tenantId,
        long datasetId,
        String policyCode,
        String policyName,
        String policyAst,
        String policyGroup,
        String policyStatus,
        long policyVersion) {
}
