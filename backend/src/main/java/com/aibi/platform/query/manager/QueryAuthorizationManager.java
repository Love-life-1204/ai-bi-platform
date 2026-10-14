package com.aibi.platform.query.manager;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.dto.DataPolicyEvaluationData;
import com.aibi.platform.identity.dto.DataPolicyEvaluationRequest;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.aibi.platform.identity.service.AccessDecisionService;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.identity.service.DataPolicyService;
import com.aibi.platform.identity.service.TaskAuthorizationService;
import com.aibi.platform.ingestion.dto.IngestionDtos.PublicationData;
import com.aibi.platform.ingestion.mapper.IngestionMapper;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.model.AuthorizedQuery;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import com.aibi.platform.semantic.mapper.SemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetField;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import com.aibi.platform.semantic.model.SemanticModels.MetricVersion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class QueryAuthorizationManager {

    private final SemanticMapper semanticMapper;
    private final IngestionMapper ingestionMapper;
    private final SemanticAccessManager accessManager;
    private final DataPolicyService policyService;
    private final TaskAuthorizationService taskAuthorizationService;
    private final AccessDecisionService accessDecisionService;
    private final ObjectMapper objectMapper;

    public QueryAuthorizationManager(SemanticMapper semanticMapper,
            IngestionMapper ingestionMapper, SemanticAccessManager accessManager,
            DataPolicyService policyService, TaskAuthorizationService taskAuthorizationService,
            AccessDecisionService accessDecisionService, ObjectMapper objectMapper) {
        this.semanticMapper = semanticMapper;
        this.ingestionMapper = ingestionMapper;
        this.accessManager = accessManager;
        this.policyService = policyService;
        this.taskAuthorizationService = taskAuthorizationService;
        this.accessDecisionService = accessDecisionService;
        this.objectMapper = objectMapper;
    }

    public AuthorizedQuery authorize(String authorization, long tenantId, QueryPlan plan) {
        DatasetVersion datasetVersion = semanticMapper
                .findDatasetVersion(tenantId, plan.datasetVersionId())
                .filter(value -> "PUBLISHED".equals(value.status()))
                .orElseThrow(() -> new NotFoundException("已发布数据集版本不存在"));
        Dataset dataset = semanticMapper.findDataset(tenantId, datasetVersion.datasetId())
                .filter(value -> "PUBLISHED".equals(value.status())
                        && value.currentVersionId() != null
                        && value.currentVersionId() == datasetVersion.id())
                .orElseThrow(() -> new ConflictException("数据集版本已不是当前发布版本"));
        TokenService.TenantClaims claims = accessManager.requireWorkspace(
                authorization, tenantId, dataset.workspaceId());
        accessManager.requirePermission(authorization, tenantId, dataset.workspaceId(),
                "dataset:read", "read");
        List<Metric> metrics = new ArrayList<>();
        List<MetricVersion> versions = new ArrayList<>();
        for (Long versionId : distinct(plan.metricVersionIds(), "指标版本不能重复")) {
            MetricVersion version = semanticMapper.findMetricVersion(tenantId, versionId)
                    .filter(value -> "PUBLISHED".equals(value.status()))
                    .orElseThrow(() -> new NotFoundException("已发布指标版本不存在: " + versionId));
            Metric metric = semanticMapper.findMetric(tenantId, version.metricId())
                    .filter(value -> "PUBLISHED".equals(value.status())
                            && value.currentVersionId() != null
                            && value.currentVersionId() == version.id())
                    .orElseThrow(() -> new ConflictException("指标版本已不是当前发布版本"));
            if (metric.workspaceId() != dataset.workspaceId()
                    || version.datasetVersionId() != datasetVersion.id()) {
                throw new BadRequestException("指标与数据集版本不匹配");
            }
            accessManager.requirePermission(authorization, tenantId, dataset.workspaceId(),
                    "metric:read", "read");
            metrics.add(metric);
            versions.add(version);
        }
        validateReferences(plan, datasetVersion, versions);
        List<Long> fieldIds = datasetVersion.fields().stream().map(DatasetField::id).toList();
        DataPolicyEvaluationData policy = policyService.evaluate(authorization, tenantId,
                new DataPolicyEvaluationRequest(dataset.workspaceId(), dataset.id(), fieldIds));
        rejectDeniedReferences(tenantId, plan, versions, datasetVersion, policy);
        PublicationData publication = ingestionMapper.publication(tenantId, dataset.id());
        if (publication.activeGeneration() == null || publication.committedPublishSeq() < 1) {
            throw new ConflictException("数据集尚无可查询的已发布数据");
        }
        return new AuthorizedQuery(tenantId, claims.memberId(),
                claims.tenantPermissionVersion(), claims.memberPermissionVersion(), plan,
                dataset, datasetVersion, List.copyOf(metrics), List.copyOf(versions),
                publication, policy, hash(plan), hash(policy));
    }

    /** Executes the same closed-plan authorization from a persisted, freshly reauthorized task. */
    public AuthorizedQuery authorize(TaskAuthorizationSnapshot snapshot, QueryPlan plan) {
        taskAuthorizationService.reauthorize(snapshot);
        long tenantId = snapshot.tenantId();
        DatasetVersion datasetVersion = semanticMapper
                .findDatasetVersion(tenantId, plan.datasetVersionId())
                .filter(value -> "PUBLISHED".equals(value.status()))
                .orElseThrow(() -> new NotFoundException("已发布数据集版本不存在"));
        Dataset dataset = semanticMapper.findDataset(tenantId, datasetVersion.datasetId())
                .filter(value -> "PUBLISHED".equals(value.status())
                        && value.currentVersionId() != null
                        && value.currentVersionId() == datasetVersion.id())
                .orElseThrow(() -> new ConflictException("数据集版本已不是当前发布版本"));
        if (snapshot.accessRequest().workspaceId() == null
                || snapshot.accessRequest().workspaceId() != dataset.workspaceId()
                || !accessManager.canAccessWorkspace(tenantId, dataset.workspaceId(), snapshot.memberId())) {
            throw new com.aibi.platform.common.exception.ForbiddenException();
        }
        requireMemberPermission(snapshot, dataset.workspaceId(), "dataset:read", "read");
        List<Metric> metrics = new ArrayList<>();
        List<MetricVersion> versions = new ArrayList<>();
        for (Long versionId : distinct(plan.metricVersionIds(), "指标版本不能重复")) {
            MetricVersion version = semanticMapper.findMetricVersion(tenantId, versionId)
                    .filter(value -> "PUBLISHED".equals(value.status()))
                    .orElseThrow(() -> new NotFoundException("已发布指标版本不存在: " + versionId));
            Metric metric = semanticMapper.findMetric(tenantId, version.metricId())
                    .filter(value -> "PUBLISHED".equals(value.status())
                            && value.currentVersionId() != null
                            && value.currentVersionId() == version.id())
                    .orElseThrow(() -> new ConflictException("指标版本已不是当前发布版本"));
            if (metric.workspaceId() != dataset.workspaceId()
                    || version.datasetVersionId() != datasetVersion.id()) {
                throw new BadRequestException("指标与数据集版本不匹配");
            }
            requireMemberPermission(snapshot, dataset.workspaceId(), "metric:read", "read");
            metrics.add(metric);
            versions.add(version);
        }
        validateReferences(plan, datasetVersion, versions);
        List<Long> fieldIds = datasetVersion.fields().stream().map(DatasetField::id).toList();
        DataPolicyEvaluationData policy = policyService.evaluateForMember(tenantId,
                snapshot.memberId(), new DataPolicyEvaluationRequest(
                        dataset.workspaceId(), dataset.id(), fieldIds));
        rejectDeniedReferences(tenantId, plan, versions, datasetVersion, policy);
        PublicationData publication = ingestionMapper.publication(tenantId, dataset.id());
        if (publication.activeGeneration() == null || publication.committedPublishSeq() < 1) {
            throw new ConflictException("数据集尚无可查询的已发布数据");
        }
        return new AuthorizedQuery(tenantId, snapshot.memberId(),
                snapshot.tenantPermissionVersion(), snapshot.memberPermissionVersion(), plan,
                dataset, datasetVersion, List.copyOf(metrics), List.copyOf(versions),
                publication, policy, hash(plan), hash(policy));
    }

    private void requireMemberPermission(TaskAuthorizationSnapshot snapshot, long workspaceId,
            String permission, String action) {
        var decision = accessDecisionService.checkForMember(snapshot.tenantId(), snapshot.memberId(),
                new AccessCheckRequest(workspaceId, permission, null, action));
        if (!decision.allowed()) {
            throw new com.aibi.platform.common.exception.ForbiddenException();
        }
    }

    private void validateReferences(QueryPlan plan, DatasetVersion dataset,
            List<MetricVersion> metrics) {
        Map<Long, DatasetField> fields = fieldsById(dataset);
        for (Long fieldId : distinct(plan.dimensionFieldIds(), "维度字段不能重复")) {
            DatasetField field = requiredField(fields, fieldId);
            if (!"DIMENSION".equals(field.role())) {
                throw new BadRequestException("分组字段必须是维度: " + fieldId);
            }
            if (metrics.stream().anyMatch(value -> !value.allowedDimensions()
                    .contains(field.stableFieldKey()))) {
                throw new BadRequestException("指标不允许使用维度: " + field.stableFieldKey());
            }
        }
        if (plan.timeRange() != null) {
            requiredField(fields, plan.timeRange().fieldId());
            if (!plan.timeRange().start().isBefore(plan.timeRange().end())) {
                throw new BadRequestException("时间范围必须满足 start < end，并使用 [start, end)");
            }
        }
        plan.filters().forEach(value -> requiredField(fields, value.fieldId()));
        Set<Long> metricIds = new HashSet<>(plan.metricVersionIds());
        for (var sort : plan.sort()) {
            if ("FIELD".equals(sort.targetType())) {
                if (!plan.dimensionFieldIds().contains(sort.targetId())) {
                    throw new BadRequestException("只能按已选择的维度排序");
                }
            } else if (!metricIds.contains(sort.targetId())) {
                throw new BadRequestException("只能按已选择的指标排序");
            }
        }
    }

    private void rejectDeniedReferences(long tenantId, QueryPlan plan, List<MetricVersion> metrics,
            DatasetVersion dataset, DataPolicyEvaluationData policy) {
        Map<Long, String> modes = new HashMap<>();
        policy.columns().forEach(value -> modes.put(value.datasetFieldId(), value.accessMode()));
        for (Long fieldId : plan.dimensionFieldIds()) {
            if ("DENY".equals(modes.get(fieldId))) {
                throw new BadRequestException("字段受列级权限限制: " + fieldId);
            }
        }
        Set<Long> indirect = new HashSet<>();
        plan.filters().forEach(value -> indirect.add(value.fieldId()));
        plan.sort().stream().filter(value -> "FIELD".equals(value.targetType()))
                .forEach(value -> indirect.add(value.targetId()));
        if (plan.timeRange() != null) {
            indirect.add(plan.timeRange().fieldId());
        }
        for (Long fieldId : indirect) {
            if (!"VISIBLE".equals(modes.get(fieldId))) {
                throw new BadRequestException("字段不能用于筛选、时间条件或排序: " + fieldId);
            }
        }
        Map<String, DatasetField> byKey = new HashMap<>();
        dataset.fields().forEach(value -> byKey.put(value.stableFieldKey(), value));
        for (MetricVersion metric : metrics) {
            Set<String> referenced = new HashSet<>();
            collectFormulaFields(tenantId, dataset.id(), metric.formulaAst(), referenced,
                    new HashSet<>());
            for (String key : referenced) {
                DatasetField field = byKey.get(key);
                if (field == null || !"VISIBLE".equals(modes.get(field.id()))) {
                    throw new BadRequestException("指标引用了无权聚合的字段: " + key);
                }
            }
        }
    }

    private void collectFormulaFields(long tenantId, long datasetVersionId,
            com.fasterxml.jackson.databind.JsonNode node, Set<String> fields,
            Set<Long> visiting) {
        if (node == null || !node.isObject()) {
            return;
        }
        if ("FIELD".equalsIgnoreCase(node.path("op").asText())) {
            fields.add(node.path("field").asText());
        }
        if ("METRIC".equalsIgnoreCase(node.path("op").asText())) {
            long dependencyId = node.path("metricVersionId").asLong(0);
            if (!visiting.add(dependencyId)) {
                throw new BadRequestException("指标依赖存在循环");
            }
            MetricVersion dependency = semanticMapper.findMetricVersion(tenantId, dependencyId)
                    .filter(value -> "PUBLISHED".equals(value.status())
                            && value.datasetVersionId() == datasetVersionId)
                    .orElseThrow(() -> new BadRequestException("指标依赖版本无效"));
            collectFormulaFields(tenantId, datasetVersionId, dependency.formulaAst(),
                    fields, visiting);
            visiting.remove(dependencyId);
        }
        node.path("args").forEach(child -> collectFormulaFields(
                tenantId, datasetVersionId, child, fields, visiting));
    }

    private Map<Long, DatasetField> fieldsById(DatasetVersion dataset) {
        Map<Long, DatasetField> result = new HashMap<>();
        dataset.fields().forEach(value -> result.put(value.id(), value));
        return result;
    }

    private DatasetField requiredField(Map<Long, DatasetField> fields, long id) {
        DatasetField field = fields.get(id);
        if (field == null) {
            throw new BadRequestException("字段不属于指定数据集版本: " + id);
        }
        return field;
    }

    private <T> List<T> distinct(List<T> values, String message) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new BadRequestException(message);
        }
        return values;
    }

    public String hash(Object value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成查询计划摘要", exception);
        }
    }
}
