package com.aibi.platform.semantic.service;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.dto.DataPolicyEvaluationData;
import com.aibi.platform.identity.dto.DataPolicyEvaluationRequest;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.identity.service.DataPolicyService;
import com.aibi.platform.semantic.dto.SemanticDtos.CreateDatasetRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.DatasetData;
import com.aibi.platform.semantic.dto.SemanticDtos.PreviewData;
import com.aibi.platform.semantic.dto.SemanticDtos.UpdateDatasetRequest;
import com.aibi.platform.semantic.dto.SemanticDtos.ValidationResult;
import com.aibi.platform.semantic.manager.DatasetPreviewManager;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import com.aibi.platform.semantic.manager.SemanticDefinitionManager;
import com.aibi.platform.semantic.mapper.SemanticMapper;
import com.aibi.platform.semantic.model.SemanticModels.Dataset;
import com.aibi.platform.semantic.model.SemanticModels.DatasetVersion;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {

    private final SemanticMapper mapper;
    private final SemanticAccessManager access;
    private final SemanticDefinitionManager definitions;
    private final DataPolicyService dataPolicyService;
    private final DatasetPreviewManager previewManager;
    private final com.aibi.platform.identity.service.ResourceAccessManager resourceAccess;

    public DatasetService(SemanticMapper mapper, SemanticAccessManager access,
            SemanticDefinitionManager definitions, DataPolicyService dataPolicyService,
            DatasetPreviewManager previewManager, com.aibi.platform.identity.service.ResourceAccessManager resourceAccess) {
        this.mapper = mapper;
        this.access = access;
        this.definitions = definitions;
        this.dataPolicyService = dataPolicyService;
        this.previewManager = previewManager;
        this.resourceAccess = resourceAccess;
    }

    @MySqlTransactional(timeout = 5)
    public DatasetData create(String authorization, long tenantId, CreateDatasetRequest request) {
        TokenService.TenantClaims claims = access.requireWorkspace(
                authorization, tenantId, request.workspaceId());
        access.requirePermission(authorization, tenantId, request.workspaceId(), "dataset:manage", "manage");
        Instant now = Instant.now();
        Dataset dataset = mapper.insertDataset(new Dataset(
                newId(), tenantId, request.workspaceId(), request.code(), request.name().trim(),
                "DRAFT", null, claims.memberId(), now, now, 0));
        DatasetVersion version = new DatasetVersion(
                newId(), tenantId, dataset.id(), 1, request.sourceObjectId(),
                definitions.fields(request.fields()), definitions.relations(request.relations()),
                rows(request.previewRows()), "", "DRAFT", null, 0);
        version = withHash(version);
        resourceAccess.register(tenantId, request.workspaceId(), claims.memberId(), "DATASET", dataset.id());
        return new DatasetData(dataset, mapper.insertDatasetVersion(version));
    }

    @MySqlTransactional(timeout = 5)
    public DatasetData update(String authorization, long tenantId, long datasetId,
            UpdateDatasetRequest request) {
        Dataset dataset = requiredDataset(tenantId, datasetId);
        access.requireWorkspace(authorization, tenantId, dataset.workspaceId());
        access.requirePermission(authorization, tenantId, dataset.workspaceId(), "dataset:manage", "manage");
        Dataset updatedDataset = mapper.updateDataset(new Dataset(
                dataset.id(), dataset.tenantId(), dataset.workspaceId(), dataset.code(), request.name().trim(),
                "DRAFT", dataset.currentVersionId(), dataset.ownerMemberId(), dataset.createdAt(),
                Instant.now(), dataset.lockVersion() + 1), request.lockVersion());
        DatasetVersion draft = draft(tenantId, datasetId);
        if (draft == null) {
            int nextVersion = mapper.listDatasetVersions(tenantId, datasetId).stream()
                    .mapToInt(DatasetVersion::versionNo).max().orElse(0) + 1;
            draft = mapper.insertDatasetVersion(withHash(new DatasetVersion(
                    newId(), tenantId, datasetId, nextVersion, request.sourceObjectId(),
                    definitions.fields(request.fields()), definitions.relations(request.relations()),
                    rows(request.previewRows()), "", "DRAFT", null, 0)));
        } else {
            DatasetVersion replacement = withHash(new DatasetVersion(
                    draft.id(), draft.tenantId(), draft.datasetId(), draft.versionNo(), request.sourceObjectId(),
                    definitions.fields(request.fields(), draft.fields()),
                    definitions.relations(request.relations(), draft.relations()),
                    rows(request.previewRows()), "", "DRAFT", null, draft.lockVersion() + 1));
            draft = mapper.updateDatasetVersion(replacement, draft.lockVersion());
        }
        return new DatasetData(updatedDataset, draft);
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<Dataset> list(String authorization, long tenantId, Long workspaceId) {
        if (workspaceId != null) {
            access.requireWorkspace(authorization, tenantId, workspaceId);
            access.requirePermission(authorization, tenantId, workspaceId, "dataset:read", "read");
            return mapper.listDatasets(tenantId, workspaceId);
        }
        TokenService.TenantClaims claims = access.requireTenant(authorization, tenantId);
        return mapper.listDatasets(tenantId, workspaceId).stream()
                .filter(value -> access.canAccessWorkspace(
                        tenantId, value.workspaceId(), claims.memberId()))
                .filter(value -> access.hasPermission(authorization, tenantId, value.workspaceId(),
                        "dataset:read", "read")).toList();
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public DatasetData get(String authorization, long tenantId, long datasetId) {
        Dataset dataset = requiredDataset(tenantId, datasetId);
        access.requireWorkspace(authorization, tenantId, dataset.workspaceId());
        access.requirePermission(authorization, tenantId, dataset.workspaceId(), "dataset:read", "read");
        return new DatasetData(dataset, draft(tenantId, datasetId));
    }

    @MySqlTransactional(timeout = 5)
    public ValidationResult validate(String authorization, long tenantId, long datasetId) {
        Dataset dataset = requiredDataset(tenantId, datasetId);
        access.requireWorkspace(authorization, tenantId, dataset.workspaceId());
        access.requirePermission(authorization, tenantId, dataset.workspaceId(), "dataset:manage", "manage");
        DatasetVersion version = requiredDraft(tenantId, datasetId);
        ValidationResult result = definitions.validate(tenantId, dataset.workspaceId(), version);
        if (result.valid()) {
            DatasetVersion validated = copyVersion(version, "VALIDATED", null, version.lockVersion() + 1);
            mapper.updateDatasetVersion(validated, version.lockVersion());
            mapper.updateDataset(copyDataset(dataset, "VALIDATED", dataset.currentVersionId()), dataset.lockVersion());
        }
        return result;
    }

    @MySqlTransactional(timeout = 5)
    public DatasetVersion publish(String authorization, long tenantId, long datasetId) {
        Dataset dataset = requiredDataset(tenantId, datasetId);
        access.requireWorkspace(authorization, tenantId, dataset.workspaceId());
        access.requirePermission(authorization, tenantId, dataset.workspaceId(), "dataset:publish", "publish");
        DatasetVersion version = requiredDraft(tenantId, datasetId);
        ValidationResult result = definitions.validate(tenantId, dataset.workspaceId(), version);
        if (!result.valid()) {
            throw new ConflictException("数据集校验未通过，不能发布");
        }
        DatasetVersion published = mapper.updateDatasetVersion(
                copyVersion(version, "PUBLISHED", Instant.now(), version.lockVersion() + 1),
                version.lockVersion());
        mapper.updateDataset(copyDataset(dataset, "PUBLISHED", published.id()), dataset.lockVersion());
        return published;
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public List<DatasetVersion> versions(String authorization, long tenantId, long datasetId) {
        Dataset dataset = requiredDataset(tenantId, datasetId);
        access.requireWorkspace(authorization, tenantId, dataset.workspaceId());
        access.requirePermission(authorization, tenantId, dataset.workspaceId(), "dataset:read", "read");
        return mapper.listDatasetVersions(tenantId, datasetId);
    }

    @MySqlTransactional(readOnly = true, timeout = 5)
    public PreviewData preview(String authorization, long tenantId, long datasetId) {
        Dataset dataset = requiredDataset(tenantId, datasetId);
        access.requireWorkspace(authorization, tenantId, dataset.workspaceId());
        access.requirePermission(authorization, tenantId, dataset.workspaceId(), "dataset:read", "read");
        DatasetVersion version = dataset.currentVersionId() == null
                ? requiredDraft(tenantId, datasetId)
                : mapper.findDatasetVersion(tenantId, dataset.currentVersionId())
                        .orElseThrow(() -> new NotFoundException("当前数据集版本不存在"));
        List<Long> fieldIds = version.fields().stream().map(field -> field.id()).toList();
        DataPolicyEvaluationData policy = dataPolicyService.evaluate(authorization, tenantId,
                new DataPolicyEvaluationRequest(dataset.workspaceId(), dataset.id(), fieldIds));
        return previewManager.apply(dataset.id(), version, policy);
    }

    private Dataset requiredDataset(long tenantId, long datasetId) {
        return mapper.findDataset(tenantId, datasetId)
                .orElseThrow(() -> new NotFoundException("数据集不存在"));
    }

    private DatasetVersion requiredDraft(long tenantId, long datasetId) {
        DatasetVersion value = draft(tenantId, datasetId);
        if (value == null) {
            throw new ConflictException("数据集没有可编辑草稿，请先修改以创建新版本");
        }
        return value;
    }

    private DatasetVersion draft(long tenantId, long datasetId) {
        return mapper.listDatasetVersions(tenantId, datasetId).stream()
                .filter(value -> "DRAFT".equals(value.status()) || "VALIDATED".equals(value.status()))
                .reduce((first, second) -> second).orElse(null);
    }

    private DatasetVersion withHash(DatasetVersion version) {
        String hash = definitions.hash(Map.of(
                "sourceObjectId", version.sourceObjectId() == null ? 0 : version.sourceObjectId(),
                "fields", version.fields(), "relations", version.relations()));
        return new DatasetVersion(version.id(), version.tenantId(), version.datasetId(), version.versionNo(),
                version.sourceObjectId(), version.fields(), version.relations(), version.previewRows(), hash,
                version.status(), version.publishedAt(), version.lockVersion());
    }

    private DatasetVersion copyVersion(DatasetVersion value, String status,
            Instant publishedAt, long lockVersion) {
        return new DatasetVersion(value.id(), value.tenantId(), value.datasetId(), value.versionNo(),
                value.sourceObjectId(), value.fields(), value.relations(), value.previewRows(),
                value.definitionHash(), status, publishedAt, lockVersion);
    }

    private Dataset copyDataset(Dataset value, String status, Long currentVersionId) {
        return new Dataset(value.id(), value.tenantId(), value.workspaceId(), value.code(), value.name(),
                status, currentVersionId, value.ownerMemberId(), value.createdAt(), Instant.now(),
                value.lockVersion() + 1);
    }

    private List<Map<String, Object>> rows(List<Map<String, Object>> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
