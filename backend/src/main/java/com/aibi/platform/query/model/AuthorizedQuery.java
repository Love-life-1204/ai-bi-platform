package com.aibi.platform.query.model;

import com.aibi.platform.identity.dto.DataPolicyEvaluationData;
import com.aibi.platform.ingestion.dto.IngestionDtos.PublicationData;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import java.util.List;

/** A candidate plan enriched exclusively from trusted server-side state. */
public record AuthorizedQuery(
        long tenantId,
        long memberId,
        long tenantPermissionVersion,
        long memberPermissionVersion,
        QueryPlan plan,
        Dataset dataset,
        DatasetVersion datasetVersion,
        List<Metric> metrics,
        List<MetricVersion> metricVersions,
        PublicationData publication,
        DataPolicyEvaluationData policy,
        String planHash,
        String policyHash) {
}
