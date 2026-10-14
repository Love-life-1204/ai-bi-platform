package com.aibi.platform.semantic.service;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateMetricRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.MetricData;
import com.aibi.platform.semantic.dto.SemanticDtos.UpdateMetricRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.ValidationResult;
import com.aibi.platform.semantic.manager.MetricExpressionValidator;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import com.aibi.platform.semantic.manager.SemanticDefinitionManager;
import com.aibi.platform.semantic.mapper.SemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class MetricService {

    private final SemanticMapper mapper;
    private final SemanticAccessManager access;
    private final SemanticDefinitionManager definitions;
    private final MetricExpressionValidator validator;

    public MetricService(SemanticMapper mapper, SemanticAccessManager access,
            SemanticDefinitionManager definitions, MetricExpressionValidator validator) {
        this.mapper = mapper;
        this.access = access;
        this.definitions = definitions;
        this.validator = validator;
    }

    @MySqlTransactional(timeout = 5)
    public MetricData create(String authorization, long tenantId, CreateMetricRequest request) {
        TokenService.TenantClaims claims = access.requireWorkspace(
                authorization, tenantId, request.workspaceId());
        access.requirePermission(authorization, tenantId, request.workspaceId(), "metric:manage", "manage");
        Instant now = Instant.now();
        Metric metric = mapper.insertMetric(new Metric(
                newId(), tenantId, request.workspaceId(), request.code(), request.name().trim(),
                request.unit().trim(), "DRAFT", null, claims.memberId(), now, now, 0));
        MetricVersion version = version(newId(), tenantId, metric.id(), 1,
                request.datasetVersionId(), request.formulaAst(), request.timeSpec(), request.currencySpec(),
                request.allowedDimensions(), request.precisionScale(), request.dependencyVersionIds(), 0);
        return new MetricData(metric, mapper.insertMetricVersion(version));
    }

    @MySqlTransactional(timeout = 5)
    public MetricData update(String authorization, long tenantId, long metricId,
            UpdateMetricRequest request) {
        Metric metric = requiredMetric(tenantId, metricId);
        access.requireWorkspace(authorization, tenantId, metric.workspaceId());
        access.requirePermission(authorization, tenantId, metric.workspaceId(), "metric:manage", "manage");
        Metric updated = mapper.updateMetric(new Metric(
                metric.id(), metric.tenantId(), metric.workspaceId(), metric.code(), request.name().trim(),
                request.unit().trim(), "DRAFT", metric.currentVersionId(), metric.ownerMemberId(),
                metric.createdAt(), Instant.now(), metric.lockVersion() + 1), request.lockVersion());
        MetricVersion draft = draft(tenantId, metricId);
        if (draft == null) {
            int nextVersion = mapper.listMetricVersions(tenantId, metricId).stream()
                    .mapToInt(MetricVersion::versionNo).max().orElse(0) + 1;
            draft = mapper.insertMetricVersion(version(newId(), tenantId, metricId, nextVersion,
                    request.datasetVersionId(), request.formulaAst(), request.timeSpec(), request.currencySpec(),
                    request.allowedDimensions(), request.precisionScale(), request.dependencyVersionIds(), 0));
        } else {
            MetricVersion replacement = version(draft.id(), tenantId, metricId, draft.versionNo(),
                    request.datasetVersionId(), request.formulaAst(), request.timeSpec(), request.currencySpec(),
                    request.allowedDimensions(), request.precisionScale(), request.dependencyVersionIds(),
                    draft.lockVersion() + 1);
            draft = mapper.updateMetricVersion(replacement, draft.lockVersion());
        }
        return new MetricData(updated, draft);
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<Metric> list(String authorization, long tenantId, Long workspaceId) {
        if (workspaceId != null) {
            access.requireWorkspace(authorization, tenantId, workspaceId);
            access.requirePermission(authorization, tenantId, workspaceId, "metric:read", "read");
            return mapper.listMetrics(tenantId, workspaceId);
        }
        TokenService.TenantClaims claims = access.requireTenant(authorization, tenantId);
        return mapper.listMetrics(tenantId, workspaceId).stream()
                .filter(value -> access.canAccessWorkspace(
                        tenantId, value.workspaceId(), claims.memberId()))
                .filter(value -> access.hasPermission(authorization, tenantId, value.workspaceId(),
                        "metric:read", "read")).toList();
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public MetricData get(String authorization, long tenantId, long metricId) {
        Metric metric = requiredMetric(tenantId, metricId);
        access.requireWorkspace(authorization, tenantId, metric.workspaceId());
        access.requirePermission(authorization, tenantId, metric.workspaceId(), "metric:read", "read");
        return new MetricData(metric, draft(tenantId, metricId));
    }

    @MySqlTransactional(timeout = 5)
    public ValidationResult validate(String authorization, long tenantId, long metricId) {
        Metric metric = requiredMetric(tenantId, metricId);
        access.requireWorkspace(authorization, tenantId, metric.workspaceId());
        access.requirePermission(authorization, tenantId, metric.workspaceId(), "metric:manage", "manage");
        MetricVersion version = requiredDraft(tenantId, metricId);
        ValidationResult result = validator.validate(tenantId, metric.workspaceId(), version);
        if (result.valid()) {
            mapper.updateMetricVersion(copyVersion(version, "VALIDATED", null, version.lockVersion() + 1),
                    version.lockVersion());
            mapper.updateMetric(copyMetric(metric, "VALIDATED", metric.currentVersionId()), metric.lockVersion());
        }
        return result;
    }

    @MySqlTransactional(timeout = 5)
    public MetricVersion publish(String authorization, long tenantId, long metricId) {
        Metric metric = requiredMetric(tenantId, metricId);
        access.requireWorkspace(authorization, tenantId, metric.workspaceId());
        access.requirePermission(authorization, tenantId, metric.workspaceId(), "metric:manage", "manage");
        MetricVersion version = requiredDraft(tenantId, metricId);
        ValidationResult result = validator.validate(tenantId, metric.workspaceId(), version);
        if (!result.valid()) {
            throw new ConflictException("指标校验未通过，不能发布");
        }
        MetricVersion published = mapper.updateMetricVersion(
                copyVersion(version, "PUBLISHED", Instant.now(), version.lockVersion() + 1),
                version.lockVersion());
        mapper.updateMetric(copyMetric(metric, "PUBLISHED", published.id()), metric.lockVersion());
        return published;
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<MetricVersion> versions(String authorization, long tenantId, long metricId) {
        Metric metric = requiredMetric(tenantId, metricId);
        access.requireWorkspace(authorization, tenantId, metric.workspaceId());
        access.requirePermission(authorization, tenantId, metric.workspaceId(), "metric:read", "read");
        return mapper.listMetricVersions(tenantId, metricId);
    }

    private MetricVersion version(long id, long tenantId, long metricId, int versionNo,
            long datasetVersionId, JsonNode formula, JsonNode time, JsonNode currency,
            List<String> dimensions, int precision, List<Long> dependencies, long lockVersion) {
        List<String> safeDimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
        List<Long> safeDependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        String hash = definitions.hash(Map.of(
                "datasetVersionId", datasetVersionId, "formula", formula, "time", time,
                "currency", currency, "dimensions", safeDimensions, "precision", precision,
                "dependencies", safeDependencies));
        return new MetricVersion(id, tenantId, metricId, versionNo, datasetVersionId,
                formula.deepCopy(), time.deepCopy(), currency.deepCopy(), safeDimensions,
                precision, safeDependencies, hash, "DRAFT", null, lockVersion);
    }

    private Metric requiredMetric(long tenantId, long metricId) {
        return mapper.findMetric(tenantId, metricId)
                .orElseThrow(() -> new NotFoundException("指标不存在"));
    }

    private MetricVersion requiredDraft(long tenantId, long metricId) {
        MetricVersion value = draft(tenantId, metricId);
        if (value == null) {
            throw new ConflictException("指标没有可编辑草稿，请先修改以创建新版本");
        }
        return value;
    }

    private MetricVersion draft(long tenantId, long metricId) {
        return mapper.listMetricVersions(tenantId, metricId).stream()
                .filter(value -> "DRAFT".equals(value.status()) || "VALIDATED".equals(value.status()))
                .reduce((first, second) -> second).orElse(null);
    }

    private MetricVersion copyVersion(MetricVersion value, String status,
            Instant publishedAt, long lockVersion) {
        return new MetricVersion(value.id(), value.tenantId(), value.metricId(), value.versionNo(),
                value.datasetVersionId(), value.formulaAst(), value.timeSpec(), value.currencySpec(),
                value.allowedDimensions(), value.precisionScale(), value.dependencyVersionIds(),
                value.definitionHash(), status, publishedAt, lockVersion);
    }

    private Metric copyMetric(Metric value, String status, Long currentVersionId) {
        return new Metric(value.id(), value.tenantId(), value.workspaceId(), value.code(), value.name(),
                value.unit(), status, currentVersionId, value.ownerMemberId(), value.createdAt(),
                Instant.now(), value.lockVersion() + 1);
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
