package com.aibi.platform.ingestion.service;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.service.ResourceAccessManager;
import com.aibi.platform.ingestion.dto.IngestionDtos.BatchData;
import com.aibi.platform.ingestion.dto.IngestionDtos.CompensationRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.CreateImportRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.CreateSyncJobRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.ErrorRow;
import com.aibi.platform.ingestion.dto.IngestionDtos.FileData;
import com.aibi.platform.ingestion.dto.IngestionDtos.FilePreviewData;
import com.aibi.platform.ingestion.dto.IngestionDtos.IdempotentEventKey;
import com.aibi.platform.ingestion.dto.IngestionDtos.ImportJobData;
import com.aibi.platform.ingestion.dto.IngestionDtos.MappingResult;
import com.aibi.platform.ingestion.dto.IngestionDtos.PublicationData;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRuleData;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRuleRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRunData;
import com.aibi.platform.ingestion.dto.IngestionDtos.SyncJobData;
import com.aibi.platform.ingestion.dto.IngestionDtos.SyncRunData;
import com.aibi.platform.ingestion.dto.IngestionDtos.TaskAccepted;
import com.aibi.platform.ingestion.dto.IngestionDtos.UpdateSyncJobRequest;
import com.aibi.platform.ingestion.entity.BatchEntity;
import com.aibi.platform.ingestion.entity.FileEntity;
import com.aibi.platform.ingestion.entity.ImportJobEntity;
import com.aibi.platform.ingestion.entity.QualityRuleEntity;
import com.aibi.platform.ingestion.entity.StagedRowEntity;
import com.aibi.platform.ingestion.entity.SyncJobEntity;
import com.aibi.platform.ingestion.entity.SyncRunEntity;
import com.aibi.platform.ingestion.manager.FieldMappingManager;
import com.aibi.platform.ingestion.manager.FileImportManager;
import com.aibi.platform.ingestion.manager.FileImportManager.ParsedTable;
import com.aibi.platform.ingestion.manager.FileImportManager.StoredUpload;
import com.aibi.platform.ingestion.manager.ImportWorkerManager;
import com.aibi.platform.ingestion.manager.QualityManager;
import com.aibi.platform.ingestion.manager.SyncWorkerManager;
import com.aibi.platform.ingestion.mapper.IngestionMapper;
import com.aibi.platform.messaging.TaskDispatcher;
import com.aibi.platform.messaging.TaskKind;
import com.aibi.platform.messaging.LocalTaskDispatcher;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IngestionServiceImpl implements IngestionService {

    private static final Set<String> MODES = Set.of("FULL", "INCREMENTAL");
    private static final Set<String> RULE_TYPES = Set.of("REQUIRED", "UNIQUE", "TYPE", "RANGE");
    private static final Set<String> SEVERITIES = Set.of("BLOCK", "WARN");
    private final IngestionMapper mapper;
    private final FileImportManager fileManager;
    private final FieldMappingManager mappingManager;
    private final QualityManager qualityManager;
    private final SyncWorkerManager worker;
    private final ImportWorkerManager importWorker;
    private final TaskDispatcher taskDispatcher;
    private final ResourceAccessManager access;
    private final com.aibi.platform.semantic.mapper.SemanticMapper semantics;
    private final IngestionTaskAuthorization taskAuthorization;
    private final com.aibi.platform.source.store.DatasourceStore datasources;
    private final AtomicLong ids = new AtomicLong(System.currentTimeMillis() * 1000);

    @Autowired
    public IngestionServiceImpl(IngestionMapper mapper, FileImportManager fileManager,
            FieldMappingManager mappingManager, QualityManager qualityManager,
            SyncWorkerManager worker, ImportWorkerManager importWorker,
            TaskDispatcher taskDispatcher, ResourceAccessManager access,
            com.aibi.platform.semantic.mapper.SemanticMapper semantics, IngestionTaskAuthorization taskAuthorization,
            com.aibi.platform.source.store.DatasourceStore datasources) {
        this.mapper = mapper;
        this.fileManager = fileManager;
        this.mappingManager = mappingManager;
        this.qualityManager = qualityManager;
        this.worker = worker;
        this.importWorker = importWorker;
        this.taskDispatcher = taskDispatcher;
        this.access = access;
        this.semantics = semantics;
        this.taskAuthorization = taskAuthorization;
        this.datasources = datasources;
    }

    public IngestionServiceImpl(IngestionMapper mapper, FileImportManager fileManager,
            FieldMappingManager mappingManager, QualityManager qualityManager,
            SyncWorkerManager worker, ImportWorkerManager importWorker, ResourceAccessManager access,
            com.aibi.platform.semantic.mapper.SemanticMapper semantics, IngestionTaskAuthorization taskAuthorization,
            com.aibi.platform.source.store.DatasourceStore datasources) {
        this(mapper, fileManager, mappingManager, qualityManager,
                worker, importWorker, new LocalTaskDispatcher(), access, semantics, taskAuthorization, datasources);
    }

    @Override
    @MySqlTransactional
    public FileData upload(String authorization, long tenantId, Long requestedWorkspace, MultipartFile upload) {
        long workspace = access.resolveWorkspace(authorization, tenantId, requestedWorkspace, "file:upload");
        var actor = access.requireWorkspace(authorization, tenantId, workspace, "file:upload");
        long id = nextId();
        StoredUpload stored = fileManager.store(tenantId, id, upload);
        String name = Path.of(upload.getOriginalFilename()).getFileName().toString();
        FileEntity file = new FileEntity(id, tenantId, name, stored.contentType(),
                stored.sizeBytes(), stored.sha256(), stored.path(), "SAFE", Instant.now(), workspace, actor.memberId());
        try {
            access.requireWorkspace(authorization, tenantId, workspace, "file:upload");
            mapper.insertFile(file);
            access.register(tenantId, workspace, actor.memberId(), "FILE", id);
            return toData(file);
        } catch (RuntimeException exception) {
            fileManager.discardStoredUpload(stored.path(), tenantId, id);
            throw exception;
        }
    }

    @Override
    public FilePreviewData preview(String authorization, long tenantId, long fileId) {
        FileEntity file = authorizedFile(authorization, tenantId, fileId, "file:read");
        FilePreviewData result = fileManager.preview(file);
        authorizedFile(authorization, tenantId, fileId, "file:read");
        return result;
    }

    @Override
    public FileEntity file(String authorization, long tenantId, long fileId) {
        return authorizedFile(authorization, tenantId, fileId, "file:download");
    }

    private FileEntity authorizedFile(String authorization, long tenantId, long fileId, String permission) {
        access.requireAnyWorkspacePermission(authorization, tenantId, permission);
        FileEntity file = requiredFile(tenantId, fileId);
        if (file.workspaceId() == null || file.ownerMemberId() == null || !"SAFE".equals(file.status())) {
            throw new NotFoundException("文件不存在或不可访问");
        }
        access.require(authorization, tenantId, file.workspaceId(), "FILE", fileId, permission);
        return file;
    }

    private FileEntity requiredFile(long tenantId, long fileId) {
        return mapper.findFile(tenantId, fileId)
                .orElseThrow(() -> new NotFoundException("文件不存在"));
    }

    @Override
    @MySqlTransactional
    public ImportJobData createImport(String authorization, long tenantId, String requestKey,
            CreateImportRequest request) {
        var actor = access.requireWorkspace(authorization, tenantId, request.workspaceId(), "import:create");
        access.requireWorkspace(authorization, tenantId, request.workspaceId(), "import:run");
        FileEntity sourceFile = authorizedFile(authorization, tenantId, request.objectFileId(), "file:read");
        if (!java.util.Objects.equals(sourceFile.workspaceId(), request.workspaceId())) {
            throw new NotFoundException("文件不存在或不可访问");
        }
        requireDataset(authorization, tenantId, request.workspaceId(), request.datasetId());
        requireIdempotencyKey(requestKey);
        ImportJobEntity existing = mapper.findImportByRequestKey(tenantId, requestKey).orElse(null);
        if (existing != null) {
            authorizedImport(authorization, tenantId, existing.id(), "import:read");
            if (existing.requesterMemberId() != actor.memberId() || existing.workspaceId() != request.workspaceId()
                    || !existing.mappings().equals(request.mappings()) || existing.strictMode() != request.strictMode()
                    || existing.objectFileId() != request.objectFileId()
                    || existing.datasetId() != request.datasetId()) {
                throw new ConflictException("幂等键已用于不同的导入请求");
            }
            return toData(existing);
        }
        mappingManager.validate(request.mappings());
        requiredFile(tenantId, request.objectFileId());
        Instant now = Instant.now();
        ImportJobEntity job = new ImportJobEntity(nextId(), tenantId, request.workspaceId(),
                request.datasetId(), actor.memberId(), request.objectFileId(), requestKey,
                request.mappings(), request.strictMode(), "PENDING", 0, 0, 0,
                null, null, now, now);
        try {
            taskDispatcher.dispatch(TaskKind.IMPORT, tenantId, job.id(),
                    () -> {
                        mapper.insertImport(job);
                        access.register(tenantId, job.workspaceId(), actor.memberId(), "IMPORT_JOB", job.id());
                        taskAuthorization.capture(authorization, tenantId, "IMPORT_JOB", job.id(), job.workspaceId(), "import:run",
                                List.of(new IngestionTaskAuthorization.ResourceCheck(job.workspaceId(), "FILE", job.objectFileId(), "file:read"),
                                        new IngestionTaskAuthorization.ResourceCheck(job.workspaceId(), "DATASET", job.datasetId(), "dataset:manage"),
                                        new IngestionTaskAuthorization.ResourceCheck(job.workspaceId(), "DATASET", job.datasetId(), "dataset:publish")));
                    },
                    () -> importWorker.submit(tenantId, job.id(), () -> processImport(job)));
        } catch (RuntimeException exception) {
            mapper.updateImport(copyImport(job, "FAILED", 0, 0, 0,
                    null, "导入队列已满"));
            throw exception;
        }
        return toData(job);
    }

    @Override
    public List<ImportJobData> listImports(String authorization, long tenantId) {
        var actor = access.requireTenant(authorization, tenantId);
        var result = mapper.listImports(tenantId).stream()
                .filter(job -> access.can(actor, job.workspaceId(), "IMPORT_JOB", job.id(), "import:read"))
                .map(this::toData).toList();
        access.requireTenant(authorization, tenantId);
        return result;
    }

    @Override
    public ImportJobData importStatus(String authorization, long tenantId, long importId) {
        return toData(authorizedImport(authorization, tenantId, importId, "import:read"));
    }

    @Override
    public ImportJobData cancelImport(String authorization, long tenantId, long importId) {
        ImportJobEntity job = authorizedImport(authorization, tenantId, importId, "import:cancel");
        if (Set.of("SUCCESS", "PARTIAL_SUCCESS", "FAILED").contains(job.status())) {
            throw new ConflictException("终态导入任务不能取消");
        }
        return toData(mapper.updateImport(copyImport(job, "CANCELLED", job.totalRows(),
                job.validRows(), job.errorRows(), job.errorFileId(), job.errorSummary())));
    }

    @Override
    @MySqlTransactional
    public SyncJobData createSyncJob(String authorization, long tenantId, CreateSyncJobRequest request) {
        access.requireAnyWorkspacePermission(authorization, tenantId, "sync:create");
        var source = datasources.find(tenantId, request.datasourceId()).filter(value -> "ACTIVE".equals(value.status()))
                .orElseThrow(() -> new NotFoundException("数据源不存在或不可访问"));
        var actor = access.requireWorkspace(authorization, tenantId, source.workspaceId(), "sync:create");
        access.require(authorization, tenantId, source.workspaceId(), "DATASOURCE", source.id(), "datasource:read");
        requireDataset(authorization, tenantId, source.workspaceId(), request.datasetId());
        String mode = validateMode(request.syncMode(), request.updatedAtField());
        validateIdentifiers(request.sourceSchema(), request.sourceTable(),
                request.primaryKeyField(), request.amountField());
        mappingManager.validate(request.mappings());
        Instant now = Instant.now();
        SyncJobEntity job = new SyncJobEntity(nextId(), tenantId, request.datasourceId(),
                request.datasetId(), request.jobCode().trim(), mode, request.sourceSchema(),
                request.sourceTable(), request.primaryKeyField(), blankToNull(request.updatedAtField()),
                blankToNull(request.amountField()), request.mappings(), request.batchSize(),
                blankToNull(request.scheduleExpression()), request.timezone(),
                request.enabled() ? "ENABLED" : "DISABLED", null, null, 0, now, now);
        mapper.insertSyncJob(job);
        access.register(tenantId, source.workspaceId(), actor.memberId(), "SYNC_JOB", job.id());
        return toData(job);
    }

    @Override
    public SyncJobData updateSyncJob(String authorization, long tenantId, long jobId,
            UpdateSyncJobRequest request) {
        SyncJobEntity current = authorizedJob(authorization, tenantId, jobId, "sync:update");
        String mode = validateMode(request.syncMode(), request.updatedAtField());
        validateIdentifiers(request.sourceSchema(), request.sourceTable(),
                request.primaryKeyField(), request.amountField());
        mappingManager.validate(request.mappings());
        SyncJobEntity updated = new SyncJobEntity(current.id(), tenantId, current.datasourceId(),
                current.datasetId(), current.jobCode(), mode, request.sourceSchema(),
                request.sourceTable(), request.primaryKeyField(), blankToNull(request.updatedAtField()),
                blankToNull(request.amountField()), request.mappings(), request.batchSize(),
                blankToNull(request.scheduleExpression()), request.timezone(), current.status(),
                current.watermarkTime(), current.watermarkPrimaryKey(), current.lockVersion() + 1,
                current.createdAt(), Instant.now());
        return toData(mapper.updateSyncJob(updated, request.lockVersion()));
    }

    @Override
    public SyncJobData setSyncJobEnabled(String authorization, long tenantId, long jobId,
            boolean enabled, long lockVersion) {
        SyncJobEntity current = authorizedJob(authorization, tenantId, jobId, "sync:update");
        SyncJobEntity updated = new SyncJobEntity(current.id(), current.tenantId(),
                current.datasourceId(), current.datasetId(), current.jobCode(), current.syncMode(),
                current.sourceSchema(), current.sourceTable(), current.primaryKeyField(),
                current.updatedAtField(), current.amountField(), current.mappings(), current.batchSize(),
                current.scheduleExpression(), current.timezone(), enabled ? "ENABLED" : "DISABLED",
                current.watermarkTime(), current.watermarkPrimaryKey(), current.lockVersion() + 1,
                current.createdAt(), Instant.now());
        return toData(mapper.updateSyncJob(updated, lockVersion));
    }

    @Override
    public List<SyncJobData> listSyncJobs(String authorization, long tenantId) {
        var actor = access.requireTenant(authorization, tenantId);
        var result = mapper.listSyncJobs(tenantId).stream()
                .filter(job -> access.canReference(actor, "SYNC_JOB", job.id(), "sync:read"))
                .map(this::toData).toList();
        access.requireTenant(authorization, tenantId);
        return result;
    }

    @Override
    public SyncJobData syncJob(String authorization, long tenantId, long jobId) {
        return toData(authorizedJob(authorization, tenantId, jobId, "sync:read"));
    }

    @Override
    @MySqlTransactional
    public TaskAccepted runSync(String authorization, long tenantId, long jobId, String requestKey) {
        SyncJobEntity job = authorizedJob(authorization, tenantId, jobId, "sync:run");
        requireIdempotencyKey(requestKey);
        if (!"ENABLED".equals(job.status())) {
            throw new ConflictException("同步任务未启用");
        }
        SyncRunEntity existing = mapper.findRunByRequestKey(tenantId, requestKey).orElse(null);
        if (existing != null) {
            if (existing.syncJobId() != jobId) {
                throw new ConflictException("幂等键已用于不同的同步任务");
            }
            return new TaskAccepted(existing.id(), existing.status());
        }
        long runId = nextId();
        SyncRunEntity run = new SyncRunEntity(runId, tenantId, jobId, 1, requestKey,
                "PENDING", null, null, 0, null, null, 0, false,
                null, null, null, Instant.now());
        taskDispatcher.dispatch(TaskKind.SYNC, tenantId, runId,
                () -> {
                    mapper.insertRun(run);
                    captureSync(authorization, job, runId);
                }, () -> worker.submit(tenantId, runId));
        return new TaskAccepted(runId, "PENDING");
    }

    @Override
    public List<SyncRunData> listRuns(String authorization, long tenantId, Long jobId) {
        var actor = access.requireTenant(authorization, tenantId);
        if (jobId != null) {
            authorizedJob(authorization, tenantId, jobId, "sync:read");
        }
        var result = mapper.listRuns(tenantId, jobId).stream()
                .filter(run -> access.canReference(actor, "SYNC_JOB", run.syncJobId(), "sync:read"))
                .map(this::toData).toList();
        access.requireTenant(authorization, tenantId);
        return result;
    }

    @Override
    public SyncRunData syncRun(String authorization, long tenantId, long runId) {
        return toData(authorizedRun(authorization, tenantId, runId, "sync:read"));
    }

    @Override
    public SyncRunData cancelRun(String authorization, long tenantId, long runId) {
        SyncRunEntity run = authorizedRun(authorization, tenantId, runId, "sync:cancel");
        if (Set.of("SUCCESS", "FAILED", "PARTIAL_SUCCESS", "CANCELLED", "TIMEOUT")
                .contains(run.status())) {
            throw new ConflictException("终态同步运行不能取消");
        }
        SyncRunEntity cancelled = new SyncRunEntity(run.id(), run.tenantId(), run.syncJobId(),
                run.attemptNo(), run.requestKey(), "CANCELLED", run.leaseOwner(), run.leaseUntil(),
                run.fencingToken(), "USER_CANCELLED", "用户请求取消", run.retryCount(),
                run.deadLetter(), run.batchId(), run.startedAt(), Instant.now(), run.createdAt());
        return toData(mapper.updateRun(cancelled));
    }

    @Override
    @MySqlTransactional
    public TaskAccepted compensate(String authorization, long tenantId, long runId, CompensationRequest request) {
        var actor = access.requireTenant(authorization, tenantId);
        SyncRunEntity failed = authorizedRun(authorization, tenantId, runId, "sync:compensate");
        var job = authorizedJob(authorization, tenantId, failed.syncJobId(), "sync:run");
        if (!"FAILED".equals(failed.status())) {
            throw new ConflictException("仅失败运行可人工补偿");
        }
        long id = nextId();
        String key = failed.requestKey() + ":manual:" + id;
        SyncRunEntity compensation = new SyncRunEntity(id, tenantId, failed.syncJobId(),
                failed.attemptNo() + 1, key, "PENDING", null, null, 0, null,
                "人工补偿: member:" + actor.memberId() + " / " + request.reason(), 0,
                false, null, null, null, Instant.now());
        taskDispatcher.dispatch(TaskKind.SYNC, tenantId, id, () -> {
            mapper.insertRun(compensation);
            captureSync(authorization, job, id);
            if (failed.deadLetter()) {
                mapper.recordCompensation(tenantId, runId, id,
                        "member:" + actor.memberId(), request.reason());
            }
        }, () -> worker.submit(tenantId, id));
        return new TaskAccepted(id, "PENDING");
    }

    @Override
    public QualityRuleData createQualityRule(String authorization, long tenantId, QualityRuleRequest request) {
        access.requireReference(authorization, tenantId, "DATASET", request.datasetId(), "quality:manage");
        access.requireReference(authorization, tenantId, "DATASET", request.datasetId(), "dataset:manage");
        String type = request.ruleType().toUpperCase(Locale.ROOT);
        String severity = request.severity().toUpperCase(Locale.ROOT);
        if (!RULE_TYPES.contains(type) || !SEVERITIES.contains(severity)) {
            throw new BadRequestException("质量规则类型或严重级别不受支持");
        }
        mappingManager.identifier(request.field(), "质量规则字段");
        QualityRuleEntity rule = new QualityRuleEntity(nextId(), tenantId, request.datasetId(),
                request.ruleCode(), type, request.field(), request.expected(), request.minimum(),
                request.maximum(), severity, "ACTIVE", Instant.now());
        return toData(mapper.insertQualityRule(rule));
    }

    @Override
    public List<QualityRuleData> listQualityRules(String authorization, long tenantId, Long datasetId) {
        var actor = access.requireTenant(authorization, tenantId);
        if (datasetId != null) {
            access.requireReference(authorization, tenantId, "DATASET", datasetId, "quality:read");
        }
        var result = mapper.listQualityRules(tenantId, datasetId).stream()
                .filter(rule -> access.canReference(actor, "DATASET", rule.datasetId(), "quality:read"))
                .map(this::toData).toList();
        access.requireTenant(authorization, tenantId);
        return result;
    }

    @Override
    public QualityRunData runQuality(String authorization, long tenantId, long batchId) {
        access.requireAnyWorkspacePermission(authorization, tenantId, "quality:manage");
        BatchEntity batch = mapper.findBatch(tenantId, batchId)
                .orElseThrow(() -> new NotFoundException("批次不存在"));
        access.requireReference(authorization, tenantId, "DATASET", batch.datasetId(), "quality:manage");
        var result = qualityManager.validate(tenantId, batch);
        access.requireTenant(authorization, tenantId);
        return result;
    }

    private SyncJobEntity authorizedJob(String authorization, long tenantId, long jobId, String action) {
        access.requireReference(authorization, tenantId, "SYNC_JOB", jobId, action);
        return requiredJob(tenantId, jobId);
    }

    private SyncRunEntity authorizedRun(String authorization, long tenantId, long runId, String action) {
        access.requireAnyWorkspacePermission(authorization, tenantId, action);
        var run = requiredRun(tenantId, runId);
        authorizedJob(authorization, tenantId, run.syncJobId(), action);
        return run;
    }

    private void captureSync(String authorization, SyncJobEntity job, long runId) {
        var asset = access.requireReference(authorization, job.tenantId(), "SYNC_JOB", job.id(), "sync:run");
        taskAuthorization.capture(authorization, job.tenantId(), "SYNC_RUN", runId, "SYNC_JOB", job.id(), asset.workspaceId(), "sync:run",
                List.of(new IngestionTaskAuthorization.ResourceCheck(asset.workspaceId(), "DATASOURCE", job.datasourceId(), "datasource:read"),
                        new IngestionTaskAuthorization.ResourceCheck(asset.workspaceId(), "DATASET", job.datasetId(), "dataset:manage"),
                        new IngestionTaskAuthorization.ResourceCheck(asset.workspaceId(), "DATASET", job.datasetId(), "dataset:publish")));
    }

    private void requireDataset(String authorization, long tenantId, long workspaceId, long datasetId) {
        access.requireWorkspace(authorization, tenantId, workspaceId, "dataset:manage");
        access.requireWorkspace(authorization, tenantId, workspaceId, "dataset:publish");
        var dataset = semantics.findDataset(tenantId, datasetId)
                .filter(value -> value.workspaceId() == workspaceId && !"ARCHIVED".equals(value.status()))
                .orElseThrow(() -> new NotFoundException("数据集不存在或不可访问"));
        access.require(authorization, tenantId, workspaceId, "DATASET", dataset.id(), "dataset:manage");
        access.require(authorization, tenantId, workspaceId, "DATASET", dataset.id(), "dataset:publish");
    }

    private ImportJobEntity authorizedImport(String authorization, long tenantId, long importId, String permission) {
        access.requireAnyWorkspacePermission(authorization, tenantId, permission);
        var job = requiredImport(tenantId, importId);
        access.require(authorization, tenantId, job.workspaceId(), "IMPORT_JOB", job.id(), permission);
        return job;
    }

    private void requireImportExecution(ImportJobEntity job) {
        if ("CANCELLED".equals(requiredImport(job.tenantId(), job.id()).status())) {
            throw new ConflictException("导入任务已取消");
        }
        taskAuthorization.require(job.tenantId(), "IMPORT_JOB", job.id());
    }

    public void consumeImport(long tenantId, long importId) {
        ImportJobEntity pending = requiredImport(tenantId, importId);
        if (!"PENDING".equals(pending.status())) {
            return;
        }
        processImport(pending);
    }

    private ImportJobEntity processImport(ImportJobEntity pending) {
        ImportJobEntity running = mapper.updateImport(copyImport(pending, "RUNNING",
                0, 0, 0, null, null));
        try {
            requireImportExecution(running);
            FileEntity file = requiredFile(running.tenantId(), running.objectFileId());
            ParsedTable table = fileManager.parse(file);
            requireImportExecution(running);
            ensureMappedFields(table, running);
            List<StagedRowEntity> events = new ArrayList<>();
            List<ErrorRow> errors = new ArrayList<>();
            long generation = nextId();
            long batchId = nextId();
            long rowNumber = 1;
            String primaryKey = running.mappings().get(0).sourceField();
            for (Map<String, String> row : table.rows()) {
                rowNumber++;
                try {
                    MappingResult mapped = mappingManager.map(running.mappings(), primaryKey, null, row);
                    IdempotentEventKey key = new IdempotentEventKey(running.tenantId(),
                            running.datasetId(), running.objectFileId(), generation, 1, mapped.businessKey(),
                            rowNumber + ":" + mapped.sourceVersion());
                    events.add(new StagedRowEntity(key, batchId, 1, mapped.values(),
                            BigDecimal.ZERO, mapped.updatedAt(), false));
                } catch (BadRequestException exception) {
                    errors.add(new ErrorRow(rowNumber, row, List.of(exception.getMessage())));
                }
            }
            Long errorFileId = errors.isEmpty() ? null : createErrorFile(running, errors);
            if (running.strictMode() && !errors.isEmpty()) {
                return mapper.updateImport(copyImport(running, "FAILED", table.rows().size(),
                        0, errors.size(), errorFileId, "严格模式阻断了含异常行的文件"));
            }
            for (var event : events) {
                requireImportExecution(running);
                mapper.insertEvent(event);
            }
            QualityManager.ReconciledValues source = qualityManager.reconcile(events);
            BatchEntity batch = new BatchEntity(batchId, running.tenantId(), running.datasetId(),
                    null, running.id(), "import-" + running.id(), generation, 1,
                    "VALIDATING", events.size(), events.size(), events.size(), errors.size(),
                    0, source.amount(), source.amount(), source.businessKeys(), source.businessKeys(),
                    "RUNNING", 1, errors.isEmpty() ? null : "异常行报告文件: " + errorFileId,
                    Instant.now(), null);
            mapper.insertBatch(batch);
            QualityRunData quality = qualityManager.validate(running.tenantId(), batch);
            if (!"PASSED".equals(quality.status())) {
                return mapper.updateImport(copyImport(running, "FAILED", table.rows().size(),
                        events.size(), errors.size(), errorFileId, "质量规则或对账未通过"));
            }
            PublicationData publication = mapper.publication(running.tenantId(), running.datasetId());
            requireImportExecution(running);
            mapper.publishCas(running.tenantId(), running.datasetId(), publication.lockVersion(),
                    generation, 1, publication.qualityVersion() + 1, batchId);
            mapper.updateBatch(new BatchEntity(batch.id(), batch.tenantId(), batch.datasetId(),
                    null, batch.importJobId(), batch.batchCode(), batch.generationId(), 1,
                    "PUBLISHED", batch.expectedRows(), batch.receivedRows(), batch.validRows(),
                    batch.rejectedRows(), batch.deduplicatedRows(), batch.sourceAmount(),
                    batch.targetAmount(), batch.sourceBusinessKeys(), batch.targetBusinessKeys(),
                    "PASSED", batch.fencingToken(), batch.errorReport(), batch.readyAt(), Instant.now()));
            String status = errors.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS";
            return mapper.updateImport(copyImport(running, status, table.rows().size(),
                    events.size(), errors.size(), errorFileId,
                    errors.isEmpty() ? null : "已跳过异常行并生成错误报告"));
        } catch (RuntimeException exception) {
            if ("CANCELLED".equals(requiredImport(running.tenantId(), running.id()).status())) {
                return requiredImport(running.tenantId(), running.id());
            }
            mapper.updateImport(copyImport(running, "FAILED", running.totalRows(),
                    running.validRows(), running.errorRows(), running.errorFileId(),
                    safeMessage(exception)));
            throw exception;
        }
    }

    private Long createErrorFile(ImportJobEntity job, List<ErrorRow> errors) {
        requireImportExecution(job);
        long fileId = nextId();
        StoredUpload report = fileManager.writeErrorReport(job.tenantId(), fileId, errors);
        FileEntity file = new FileEntity(fileId, job.tenantId(),
                "import-" + job.id() + "-errors.csv", report.contentType(), report.sizeBytes(),
                report.sha256(), report.path(), "SAFE", Instant.now(), job.workspaceId(), job.requesterMemberId());
        mapper.insertFile(file);
        access.register(job.tenantId(), job.workspaceId(), job.requesterMemberId(), "FILE", fileId);
        return fileId;
    }

    private void ensureMappedFields(ParsedTable table, ImportJobEntity job) {
        List<String> missing = job.mappings().stream().map(mapping -> mapping.sourceField())
                .filter(field -> !table.headers().contains(field)).toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("文件缺少已确认字段: " + String.join(",", missing));
        }
    }

    private String validateMode(String value, String updatedAtField) {
        String mode = value.toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw new BadRequestException("同步模式仅支持 FULL 或 INCREMENTAL");
        }
        if ("INCREMENTAL".equals(mode)
                && (updatedAtField == null || updatedAtField.isBlank())) {
            throw new BadRequestException("增量同步必须配置更新时间字段");
        }
        return mode;
    }

    private void validateIdentifiers(String schema, String table, String primaryKey, String amount) {
        mappingManager.identifier(schema, "源 Schema");
        mappingManager.identifier(table, "源表");
        mappingManager.identifier(primaryKey, "稳定主键");
        if (amount != null && !amount.isBlank()) {
            mappingManager.identifier(amount, "金额字段");
        }
    }

    private void requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new BadRequestException("Idempotency-Key 必填且不能超过 128 字符");
        }
    }

    private ImportJobEntity copyImport(ImportJobEntity job, String status, long total,
            long valid, long errors, Long errorFileId, String summary) {
        return new ImportJobEntity(job.id(), job.tenantId(), job.workspaceId(), job.datasetId(),
                job.requesterMemberId(), job.objectFileId(), job.requestKey(), job.mappings(),
                job.strictMode(), status, total, valid, errors, errorFileId, summary,
                job.createdAt(), Instant.now());
    }

    private ImportJobEntity requiredImport(long tenantId, long importId) {
        return mapper.findImport(tenantId, importId)
                .orElseThrow(() -> new NotFoundException("导入任务不存在"));
    }

    private SyncJobEntity requiredJob(long tenantId, long jobId) {
        return mapper.findSyncJob(tenantId, jobId)
                .orElseThrow(() -> new NotFoundException("同步任务不存在"));
    }

    private SyncRunEntity requiredRun(long tenantId, long runId) {
        return mapper.findRun(tenantId, runId)
                .orElseThrow(() -> new NotFoundException("同步运行不存在"));
    }

    private FileData toData(FileEntity file) {
        return new FileData(file.id(), file.fileName(), file.contentType(), file.sizeBytes(),
                file.sha256(), file.status(), file.createdAt(), file.workspaceId(), file.ownerMemberId());
    }

    private ImportJobData toData(ImportJobEntity job) {
        return new ImportJobData(job.id(), job.datasetId(), job.objectFileId(), job.status(),
                job.totalRows(), job.validRows(), job.errorRows(), job.errorFileId(),
                job.errorSummary(), job.createdAt(), job.updatedAt());
    }

    private SyncJobData toData(SyncJobEntity job) {
        return new SyncJobData(job.id(), job.datasourceId(), job.datasetId(), job.jobCode(),
                job.syncMode(), job.sourceSchema() + "." + job.sourceTable(),
                job.primaryKeyField(), job.updatedAtField(), job.amountField(), job.mappings(),
                job.batchSize(), job.scheduleExpression(), job.timezone(), job.status(),
                job.watermarkTime(), job.watermarkPrimaryKey(), job.lockVersion(),
                job.createdAt(), job.updatedAt());
    }

    private SyncRunData toData(SyncRunEntity run) {
        BatchData batch = mapper.findBatchByRun(run.tenantId(), run.id()).map(this::toData).orElse(null);
        return new SyncRunData(run.id(), run.syncJobId(), run.attemptNo(), run.status(),
                run.leaseOwner(), run.leaseUntil(), run.fencingToken(), run.errorCode(),
                run.errorSummary(), run.retryCount(), run.deadLetter(), batch,
                run.startedAt(), run.finishedAt(), run.createdAt());
    }

    private BatchData toData(BatchEntity batch) {
        return new BatchData(batch.id(), batch.generationId(), batch.publishSeq(), batch.status(),
                batch.expectedRows(), batch.receivedRows(), batch.validRows(), batch.rejectedRows(),
                batch.deduplicatedRows(), batch.sourceAmount().toPlainString(),
                batch.targetAmount().toPlainString(), batch.sourceBusinessKeys(),
                batch.targetBusinessKeys(), batch.qualityStatus(), batch.errorReport(),
                batch.publishedAt());
    }

    private QualityRuleData toData(QualityRuleEntity rule) {
        return new QualityRuleData(rule.id(), rule.datasetId(), rule.ruleCode(), rule.ruleType(),
                rule.field(), rule.expected(), rule.minimum(), rule.maximum(), rule.severity(),
                rule.status(), rule.createdAt());
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "导入失败" : message.substring(0,
                Math.min(500, message.length()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private long nextId() {
        return ids.incrementAndGet();
    }
}
