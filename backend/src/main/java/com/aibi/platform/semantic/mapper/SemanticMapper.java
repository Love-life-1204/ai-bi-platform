package com.aibi.platform.semantic.mapper;

import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.aibi.platform.semantic.model.SemanticModels.Term;
import java.util.List;
import java.util.Optional;

public interface SemanticMapper {

    Dataset insertDataset(Dataset dataset);

    Dataset updateDataset(Dataset dataset, long expectedLockVersion);

    Optional<Dataset> findDataset(long tenantId, long datasetId);

    List<Dataset> listDatasets(long tenantId, Long workspaceId);

    DatasetVersion insertDatasetVersion(DatasetVersion version);

    DatasetVersion updateDatasetVersion(DatasetVersion version, long expectedLockVersion);

    Optional<DatasetVersion> findDatasetVersion(long tenantId, long versionId);

    List<DatasetVersion> listDatasetVersions(long tenantId, long datasetId);

    Metric insertMetric(Metric metric);

    Metric updateMetric(Metric metric, long expectedLockVersion);

    Optional<Metric> findMetric(long tenantId, long metricId);

    List<Metric> listMetrics(long tenantId, Long workspaceId);

    MetricVersion insertMetricVersion(MetricVersion version);

    MetricVersion updateMetricVersion(MetricVersion version, long expectedLockVersion);

    Optional<MetricVersion> findMetricVersion(long tenantId, long versionId);

    List<MetricVersion> listMetricVersions(long tenantId, long metricId);

    List<MetricVersion> listAllMetricVersions(long tenantId);

    Term insertTerm(Term term);

    Term updateTerm(Term term, long expectedLockVersion);

    Optional<Term> findTerm(long tenantId, long termId);

    List<Term> listTerms(long tenantId, Long workspaceId);
}
