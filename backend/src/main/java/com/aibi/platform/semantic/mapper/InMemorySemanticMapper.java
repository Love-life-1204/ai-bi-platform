package com.aibi.platform.semantic.mapper;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.aibi.platform.semantic.model.SemanticModels.Term;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemorySemanticMapper implements SemanticMapper {

    private final ConcurrentMap<Long, Dataset> datasets = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, DatasetVersion> datasetVersions = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Metric> metrics = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, MetricVersion> metricVersions = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Term> terms = new ConcurrentHashMap<>();

    @Override
    public Dataset insertDataset(Dataset value) {
        rejectDuplicateDataset(value, 0);
        if (datasets.putIfAbsent(value.id(), value) != null) {
            throw new ConflictException("数据集已存在");
        }
        return value;
    }

    @Override
    public Dataset updateDataset(Dataset value, long expectedLockVersion) {
        rejectDuplicateDataset(value, value.id());
        return replace(datasets, value, value.id(), value.tenantId(), expectedLockVersion,
                Dataset::tenantId, Dataset::lockVersion, "数据集不存在");
    }

    @Override
    public Optional<Dataset> findDataset(long tenantId, long datasetId) {
        return tenantValue(datasets, datasetId, tenantId, Dataset::tenantId);
    }

    @Override
    public List<Dataset> listDatasets(long tenantId, Long workspaceId) {
        return datasets.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> workspaceId == null || value.workspaceId() == workspaceId)
                .sorted(Comparator.comparing(Dataset::code)).toList();
    }

    @Override
    public DatasetVersion insertDatasetVersion(DatasetVersion value) {
        if (datasetVersions.values().stream().anyMatch(existing -> existing.tenantId() == value.tenantId()
                && existing.datasetId() == value.datasetId() && existing.versionNo() == value.versionNo())
                || datasetVersions.putIfAbsent(value.id(), value) != null) {
            throw new ConflictException("数据集版本号已存在");
        }
        return value;
    }

    @Override
    public DatasetVersion updateDatasetVersion(DatasetVersion value, long expectedLockVersion) {
        return replace(datasetVersions, value, value.id(), value.tenantId(), expectedLockVersion,
                DatasetVersion::tenantId, DatasetVersion::lockVersion, "数据集版本不存在");
    }

    @Override
    public Optional<DatasetVersion> findDatasetVersion(long tenantId, long versionId) {
        return tenantValue(datasetVersions, versionId, tenantId, DatasetVersion::tenantId);
    }

    @Override
    public List<DatasetVersion> listDatasetVersions(long tenantId, long datasetId) {
        return datasetVersions.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> value.datasetId() == datasetId)
                .sorted(Comparator.comparingInt(DatasetVersion::versionNo)).toList();
    }

    @Override
    public Metric insertMetric(Metric value) {
        rejectDuplicateMetric(value, 0);
        if (metrics.putIfAbsent(value.id(), value) != null) {
            throw new ConflictException("指标已存在");
        }
        return value;
    }

    @Override
    public Metric updateMetric(Metric value, long expectedLockVersion) {
        rejectDuplicateMetric(value, value.id());
        return replace(metrics, value, value.id(), value.tenantId(), expectedLockVersion,
                Metric::tenantId, Metric::lockVersion, "指标不存在");
    }

    @Override
    public Optional<Metric> findMetric(long tenantId, long metricId) {
        return tenantValue(metrics, metricId, tenantId, Metric::tenantId);
    }

    @Override
    public List<Metric> listMetrics(long tenantId, Long workspaceId) {
        return metrics.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> workspaceId == null || value.workspaceId() == workspaceId)
                .sorted(Comparator.comparing(Metric::code)).toList();
    }

    @Override
    public MetricVersion insertMetricVersion(MetricVersion value) {
        if (metricVersions.values().stream().anyMatch(existing -> existing.tenantId() == value.tenantId()
                && existing.metricId() == value.metricId() && existing.versionNo() == value.versionNo())
                || metricVersions.putIfAbsent(value.id(), value) != null) {
            throw new ConflictException("指标版本号已存在");
        }
        return value;
    }

    @Override
    public MetricVersion updateMetricVersion(MetricVersion value, long expectedLockVersion) {
        return replace(metricVersions, value, value.id(), value.tenantId(), expectedLockVersion,
                MetricVersion::tenantId, MetricVersion::lockVersion, "指标版本不存在");
    }

    @Override
    public Optional<MetricVersion> findMetricVersion(long tenantId, long versionId) {
        return tenantValue(metricVersions, versionId, tenantId, MetricVersion::tenantId);
    }

    @Override
    public List<MetricVersion> listMetricVersions(long tenantId, long metricId) {
        return metricVersions.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> value.metricId() == metricId)
                .sorted(Comparator.comparingInt(MetricVersion::versionNo)).toList();
    }

    @Override
    public List<MetricVersion> listAllMetricVersions(long tenantId) {
        return metricVersions.values().stream().filter(value -> value.tenantId() == tenantId).toList();
    }

    @Override
    public Term insertTerm(Term value) {
        rejectDuplicateTerm(value, 0);
        if (terms.putIfAbsent(value.id(), value) != null) {
            throw new ConflictException("业务术语已存在");
        }
        return value;
    }

    @Override
    public Term updateTerm(Term value, long expectedLockVersion) {
        rejectDuplicateTerm(value, value.id());
        return replace(terms, value, value.id(), value.tenantId(), expectedLockVersion,
                Term::tenantId, Term::lockVersion, "业务术语不存在");
    }

    @Override
    public Optional<Term> findTerm(long tenantId, long termId) {
        return tenantValue(terms, termId, tenantId, Term::tenantId);
    }

    @Override
    public List<Term> listTerms(long tenantId, Long workspaceId) {
        return terms.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> workspaceId == null || value.workspaceId() == workspaceId)
                .sorted(Comparator.comparing(Term::code)).toList();
    }

    private void rejectDuplicateDataset(Dataset value, long ignoredId) {
        if (datasets.values().stream().anyMatch(existing -> existing.id() != ignoredId
                && existing.tenantId() == value.tenantId() && existing.workspaceId() == value.workspaceId()
                && existing.code().equals(value.code()))) {
            throw new ConflictException("数据集编码已存在");
        }
    }

    private void rejectDuplicateMetric(Metric value, long ignoredId) {
        if (metrics.values().stream().anyMatch(existing -> existing.id() != ignoredId
                && existing.tenantId() == value.tenantId() && existing.workspaceId() == value.workspaceId()
                && existing.code().equals(value.code()))) {
            throw new ConflictException("指标编码已存在");
        }
    }

    private void rejectDuplicateTerm(Term value, long ignoredId) {
        if (terms.values().stream().anyMatch(existing -> existing.id() != ignoredId
                && existing.tenantId() == value.tenantId() && existing.workspaceId() == value.workspaceId()
                && existing.code().equals(value.code()))) {
            throw new ConflictException("业务术语编码已存在");
        }
    }

    private <T> Optional<T> tenantValue(ConcurrentMap<Long, T> values, long id,
            long tenantId, Function<T, Long> tenant) {
        return Optional.ofNullable(values.get(id)).filter(value -> tenant.apply(value) == tenantId);
    }

    private <T> T replace(ConcurrentMap<Long, T> values, T replacement, long id, long tenantId,
            long expectedVersion, Function<T, Long> tenant, Function<T, Long> version,
            String missingMessage) {
        return values.compute(id, (ignored, current) -> {
            if (current == null || tenant.apply(current) != tenantId) {
                throw new NotFoundException(missingMessage);
            }
            if (version.apply(current) != expectedVersion) {
                throw new ApiException(ApiErrorCode.OPTIMISTIC_LOCK_CONFLICT);
            }
            return replacement;
        });
    }
}
