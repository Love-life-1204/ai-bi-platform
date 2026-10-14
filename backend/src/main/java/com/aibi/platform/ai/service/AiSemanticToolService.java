package com.aibi.platform.ai.service;

import com.aibi.platform.common.json.ResourceIds;
import com.aibi.platform.ai.dto.AiDtos.SemanticCatalogData;
import com.aibi.platform.ai.dto.AiDtos.SemanticDatasetData;
import com.aibi.platform.ai.dto.AiDtos.SemanticFieldData;
import com.aibi.platform.ai.dto.AiDtos.SemanticMetricData;
import com.aibi.platform.ai.mapper.AiStore;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.service.AccessDecisionService;
import com.aibi.platform.identity.service.TaskAuthorizationService;
import com.aibi.platform.semantic.mapper.SemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.Metric;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiSemanticToolService {

    private final AiStore store;
    private final TaskAuthorizationService taskAuthorization;
    private final AccessDecisionService accessDecision;
    private final SemanticMapper semantic;

    public AiSemanticToolService(AiStore store, TaskAuthorizationService taskAuthorization,
            AccessDecisionService accessDecision, SemanticMapper semantic) {
        this.store = store;
        this.taskAuthorization = taskAuthorization;
        this.accessDecision = accessDecision;
        this.semantic = semantic;
    }

    public SemanticCatalogData authorized(long taskId, Long requestedDatasetId) {
        var task = findTask(taskId);
        var snapshot = store.authorization(task.tenantId(), taskId)
                .orElseThrow(() -> new NotFoundException("AI 任务授权快照不存在"));
        taskAuthorization.reauthorize(snapshot);
        Long workspaceId = snapshot.accessRequest().workspaceId();
        if (workspaceId == null) {
            throw new ForbiddenException();
        }
        require(snapshot.tenantId(), snapshot.memberId(), workspaceId, "dataset:read", "read");
        require(snapshot.tenantId(), snapshot.memberId(), workspaceId, "metric:read", "read");
        List<SemanticDatasetData> datasets = new ArrayList<>();
        Map<Long, com.aibi.platform.semantic.model.SemanticModels.DatasetVersion> versions = new HashMap<>();
        for (Dataset dataset : semantic.listDatasets(snapshot.tenantId(), workspaceId)) {
            if (!"PUBLISHED".equals(dataset.status()) || dataset.currentVersionId() == null
                    || requestedDatasetId != null && requestedDatasetId != dataset.id()) {
                continue;
            }
            var version = semantic.findDatasetVersion(snapshot.tenantId(), dataset.currentVersionId())
                    .filter(value -> "PUBLISHED".equals(value.status())).orElse(null);
            if (version == null) {
                continue;
            }
            versions.put(version.id(), version);
            datasets.add(new SemanticDatasetData(dataset.id(), version.id(), dataset.code(), dataset.name(),
                    version.fields().stream().map(field -> new SemanticFieldData(
                            field.id(), field.stableFieldKey(), field.businessName(), field.dataType(),
                            "TIME".equals(field.role()) ? "TIME" : field.role())).toList()));
        }
        List<SemanticMetricData> metrics = new ArrayList<>();
        List<Metric> publishedMetrics = semantic.listMetrics(snapshot.tenantId(), workspaceId).stream()
                .filter(value -> "PUBLISHED".equals(value.status()) && value.currentVersionId() != null).toList();
        var terms = semantic.listTerms(snapshot.tenantId(), workspaceId).stream()
                .filter(value -> "PUBLISHED".equals(value.status())).toList();
        for (Metric metric : publishedMetrics) {
            var version = semantic.findMetricVersion(snapshot.tenantId(), metric.currentVersionId())
                    .filter(value -> "PUBLISHED".equals(value.status())
                            && versions.containsKey(value.datasetVersionId())).orElse(null);
            if (version == null) {
                continue;
            }
            var datasetVersion = versions.get(version.datasetVersionId());
            Map<String, Long> fieldsByKey = new HashMap<>();
            datasetVersion.fields().forEach(field -> fieldsByKey.put(field.stableFieldKey(), field.id()));
            List<Long> dimensions = version.allowedDimensions().stream()
                    .map(fieldsByKey::get).filter(java.util.Objects::nonNull).toList();
            Long timeField = ResourceIds.valid(version.timeSpec().path("fieldId"))
                    ? ResourceIds.parse(version.timeSpec().path("fieldId")) : null;
            List<String> aliases = terms.stream()
                    .filter(term -> term.targetAssetId() != null && term.targetAssetId() == metric.id())
                    .flatMap(term -> java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(term.name()), term.synonyms().stream())).distinct().toList();
            metrics.add(new SemanticMetricData(version.id(), metric.id(), version.datasetVersionId(),
                    metric.code(), metric.name(), metric.unit(), aliases, dimensions, timeField));
        }
        List<Map<String, Object>> termData = terms.stream().map(term -> Map.<String, Object>of(
                "name", term.name(), "definition", term.definition(), "synonyms", term.synonyms())).toList();
        String version = Integer.toHexString((datasets.toString() + metrics).hashCode());
        return new SemanticCatalogData(datasets, metrics, termData, version);
    }

    private com.aibi.platform.ai.model.AiModels.Task findTask(long taskId) {
        return store.findTaskByGlobalId(taskId)
                .orElseThrow(() -> new NotFoundException("AI 任务不存在"));
    }

    private void require(long tenantId, long memberId, long workspaceId,
            String permission, String action) {
        var decision = accessDecision.checkForMember(tenantId, memberId,
                new AccessCheckRequest(workspaceId, permission, null, action));
        if (!decision.allowed()) {
            throw new ForbiddenException();
        }
    }
}

