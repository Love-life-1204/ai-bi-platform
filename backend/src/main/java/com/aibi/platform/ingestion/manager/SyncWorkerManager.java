package com.aibi.platform.ingestion.manager;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.TooManyRequestsException;
import com.aibi.platform.ingestion.dto.IngestionDtos.IdempotentEventKey;
import com.aibi.platform.ingestion.dto.IngestionDtos.LeaseClaim;
import com.aibi.platform.ingestion.dto.IngestionDtos.MappingResult;
import com.aibi.platform.ingestion.dto.IngestionDtos.PublicationData;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRunData;
import com.aibi.platform.ingestion.dto.IngestionDtos.SourcePage;
import com.aibi.platform.ingestion.entity.BatchEntity;
import com.aibi.platform.ingestion.entity.StagedRowEntity;
import com.aibi.platform.ingestion.entity.SyncJobEntity;
import com.aibi.platform.ingestion.entity.SyncRunEntity;
import com.aibi.platform.ingestion.mapper.IngestionMapper;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class SyncWorkerManager {

    private static final Set<String> ACTIVE_JOB_STATUSES = Set.of("ENABLED", "ACTIVE");
    private final IngestionMapper mapper;
    private final SourceTableReader sourceReader;
    private final FieldMappingManager mappingManager;
    private final QualityManager qualityManager;
    private final IngestionProperties properties;
    private final com.aibi.platform.ingestion.service.IngestionTaskAuthorization authorization;
    private final String workerId = "sync-" + UUID.randomUUID();
    private final AtomicLong ids = new AtomicLong(System.currentTimeMillis() * 1000);
    private final ExecutorService executor = new ThreadPoolExecutor(2, 4, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.AbortPolicy());

    public SyncWorkerManager(IngestionMapper mapper, SourceTableReader sourceReader,
            FieldMappingManager mappingManager, QualityManager qualityManager,
            IngestionProperties properties, com.aibi.platform.ingestion.service.IngestionTaskAuthorization authorization) {
        this.mapper = mapper;
        this.sourceReader = sourceReader;
        this.mappingManager = mappingManager;
        this.qualityManager = qualityManager;
        this.properties = properties;
        this.authorization = authorization;
    }

    public void submit(long tenantId, long runId) {
        try {
            executor.execute(() -> execute(tenantId, runId));
        } catch (RejectedExecutionException exception) {
            SyncRunEntity run = requiredRun(tenantId, runId);
            mapper.updateRun(finish(run, "FAILED", "SYNC_QUEUE_FULL",
                    "同步队列已满，请稍后重试", true));
            throw new TooManyRequestsException(5);
        }
    }

    public void execute(long tenantId, long runId) {
        SyncRunEntity original = requiredRun(tenantId, runId);
        LeaseClaim lease = mapper.claimLease(tenantId, runId, workerId, Instant.now(),
                        Duration.ofSeconds(properties.getLeaseSeconds()))
                .orElse(null);
        if (lease == null) {
            return;
        }
        try {
            requireExecution(tenantId, runId);
            runWithLease(original, lease);
        } catch (RuntimeException exception) {
            handleFailure(requiredRun(tenantId, runId), exception);
        }
    }

    private void runWithLease(SyncRunEntity initialRun, LeaseClaim lease) {
        SyncJobEntity job = mapper.findSyncJob(initialRun.tenantId(), initialRun.syncJobId())
                .orElseThrow(() -> new BadRequestException("同步任务不存在"));
        if (!ACTIVE_JOB_STATUSES.contains(job.status())) {
            throw new ConflictException("同步任务未启用");
        }
        PublicationData publication = mapper.publication(job.tenantId(), job.datasetId());
        boolean full = "FULL".equals(job.syncMode());
        if (!full && publication.activeGeneration() == null) {
            throw new ConflictException("增量同步前必须先完成一次全量同步");
        }
        long generation = full ? nextId() : publication.activeGeneration();
        long publishSeq = full ? 1 : publication.committedPublishSeq() + 1;
        long batchId = nextId();
        BatchEntity batch = emptyBatch(job, initialRun.id(), batchId, generation,
                publishSeq, lease.fencingToken());
        mapper.insertBatch(batch);
        mapper.updateRun(withBatch(requiredRun(job.tenantId(), initialRun.id()), batchId));

        Extraction extraction = extract(job, batch, lease);
        List<StagedRowEntity> targetRows = mapper.listBatchRows(job.tenantId(), batchId);
        QualityManager.ReconciledValues target = qualityManager.reconcile(targetRows);
        BatchEntity loaded = new BatchEntity(batch.id(), batch.tenantId(), batch.datasetId(),
                batch.syncRunId(), null, batch.batchCode(), batch.generationId(), batch.publishSeq(),
                "VALIDATING", extraction.distinctEvents(), target.rows(), target.rows(),
                extraction.rejectedRows(), extraction.duplicates(), extraction.amount(), target.amount(),
                extraction.businessKeys(), target.businessKeys(), "RUNNING", batch.fencingToken(),
                extraction.errorReport(), Instant.now(), null);
        mapper.updateBatch(loaded);
        QualityRunData quality = qualityManager.validate(job.tenantId(), loaded);
        if (!"PASSED".equals(quality.status())) {
            mapper.updateBatch(withQuality(loaded, "FAILED", "QUALITY_FAILED", null));
            throw new ConflictException("质量校验或对账未通过");
        }
        assertLease(job.tenantId(), initialRun.id(), lease.fencingToken());
        Instant now = Instant.now();
        BatchEntity publishedBatch = withQuality(loaded, "PUBLISHED", "PASSED", now);
        SyncJobEntity publishedJob = advancedWatermark(job, extraction, now);
        requireExecution(job.tenantId(), initialRun.id());
        mapper.publishSyncCas(publishedJob, job.lockVersion(), publishedBatch,
                publication.lockVersion(), publication.qualityVersion() + 1);
        SyncRunEntity current = requiredRun(job.tenantId(), initialRun.id());
        mapper.updateRun(new SyncRunEntity(current.id(), current.tenantId(), current.syncJobId(),
                current.attemptNo(), current.requestKey(), "SUCCESS", current.leaseOwner(),
                current.leaseUntil(), current.fencingToken(), null, null, current.retryCount(),
                false, current.batchId(), current.startedAt(), now, current.createdAt()));
    }

    private Extraction extract(SyncJobEntity job, BatchEntity batch, LeaseClaim lease) {
        Instant cursorTime = "INCREMENTAL".equals(job.syncMode()) ? job.watermarkTime() : null;
        String cursorKey = "INCREMENTAL".equals(job.syncMode())
                ? job.watermarkPrimaryKey() : null;
        Map<IdempotentEventKey, StagedRowEntity> distinctSource = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        long duplicates = 0;
        boolean more;
        do {
            renew(job.tenantId(), lease);
            requireExecution(job.tenantId(), lease.runId());
            SourcePage page = sourceReader.read(job, cursorTime, cursorKey, job.batchSize());
            requireExecution(job.tenantId(), lease.runId());
            more = page.hasMore();
            for (Map<String, Object> row : page.rows()) {
                requireExecution(job.tenantId(), lease.runId());
                try {
                    MappingResult mapped = mappingManager.map(job, row);
                    BigDecimal amount = amount(job, row);
                    IdempotentEventKey key = new IdempotentEventKey(job.tenantId(), job.datasetId(),
                            job.datasourceId(), batch.generationId(), batch.publishSeq(), mapped.businessKey(),
                            mapped.sourceVersion());
                    StagedRowEntity event = new StagedRowEntity(key, batch.id(),
                            lease.fencingToken(), mapped.values(), amount,
                            mapped.updatedAt(), mapped.deleted());
                    StagedRowEntity previous = distinctSource.putIfAbsent(key, event);
                    if (previous != null || !mapper.insertEvent(event)) {
                        duplicates++;
                    }
                    cursorTime = mapped.updatedAt();
                    cursorKey = mapped.businessKey();
                } catch (BadRequestException exception) {
                    errors.add(exception.getMessage());
                }
            }
            if (page.rows().isEmpty()) {
                more = false;
            }
        } while (more);
        if (!errors.isEmpty()) {
            throw new BadRequestException("存在无法映射的源数据行: " + String.join("; ",
                    errors.stream().limit(10).toList()));
        }
        QualityManager.ReconciledValues source = qualityManager.reconcile(
                new ArrayList<>(distinctSource.values()));
        return new Extraction(distinctSource.size(), source.businessKeys(), source.amount(),
                0, duplicates, null, cursorTime, cursorKey);
    }

    private SyncJobEntity advancedWatermark(SyncJobEntity job, Extraction extraction, Instant now) {
        return new SyncJobEntity(job.id(), job.tenantId(), job.datasourceId(), job.datasetId(),
                job.jobCode(), job.syncMode(), job.sourceSchema(), job.sourceTable(),
                job.primaryKeyField(), job.updatedAtField(), job.amountField(), job.mappings(),
                job.batchSize(), job.scheduleExpression(), job.timezone(), job.status(),
                "INCREMENTAL".equals(job.syncMode()) ? extraction.lastTime() : job.watermarkTime(),
                "INCREMENTAL".equals(job.syncMode()) ? extraction.lastKey() : job.watermarkPrimaryKey(),
                job.lockVersion() + 1, job.createdAt(), now);
    }

    private void requireExecution(long tenantId, long runId) {
        if ("CANCELLED".equals(requiredRun(tenantId, runId).status())) {
            throw new ConflictException("同步运行已取消");
        }
        authorization.require(tenantId, "SYNC_RUN", runId);
    }

    private void handleFailure(SyncRunEntity run, RuntimeException exception) {
        if ("CANCELLED".equals(run.status())) {
            return;
        }
        int retryCount = run.retryCount() + 1;
        boolean retryable = retryable(exception);
        boolean deadLetter = !retryable || retryCount >= properties.getMaxAttempts();
        String message = safeMessage(exception);
        mapper.updateRun(new SyncRunEntity(run.id(), run.tenantId(), run.syncJobId(),
                run.attemptNo(), run.requestKey(), "FAILED", run.leaseOwner(), run.leaseUntil(),
                run.fencingToken(), classify(exception), message, retryCount, deadLetter,
                run.batchId(), run.startedAt(), Instant.now(), run.createdAt()));
        if (!deadLetter) {
            long retryId = nextId();
            SyncRunEntity retry = new SyncRunEntity(retryId, run.tenantId(), run.syncJobId(),
                    run.attemptNo() + 1, run.requestKey() + ":retry:" + retryCount,
                    "PENDING", null, null, 0, null, null, retryCount,
                    false, null, null, null, Instant.now());
            authorization.copy(run.tenantId(), "SYNC_RUN", run.id(), retryId);
            mapper.insertRun(retry);
            submit(run.tenantId(), retryId);
        }
    }

    private void renew(long tenantId, LeaseClaim lease) {
        boolean renewed = mapper.renewLease(tenantId, lease.runId(), lease.owner(),
                lease.fencingToken(), Instant.now(), Duration.ofSeconds(properties.getLeaseSeconds()));
        if (!renewed) {
            throw new ConflictException("Worker 租约已失效，拒绝继续写入");
        }
    }

    private void assertLease(long tenantId, long runId, long fencingToken) {
        SyncRunEntity run = requiredRun(tenantId, runId);
        if (run.fencingToken() != fencingToken || run.leaseUntil() == null
                || !run.leaseUntil().isAfter(Instant.now())) {
            throw new ConflictException("fencing token 已过期，禁止发布批次");
        }
    }

    private BigDecimal amount(SyncJobEntity job, Map<String, Object> row) {
        if (job.amountField() == null || job.amountField().isBlank()) {
            return BigDecimal.ZERO;
        }
        Object value = row.get(job.amountField());
        try {
            return value == null || value.toString().isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            throw new BadRequestException(job.amountField() + " 不是合法金额");
        }
    }

    private BatchEntity emptyBatch(SyncJobEntity job, long runId, long batchId,
            long generation, long publishSeq, long fencingToken) {
        return new BatchEntity(batchId, job.tenantId(), job.datasetId(), runId, null,
                job.id() + "-" + generation + "-" + publishSeq, generation, publishSeq,
                "WRITING", 0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, "PENDING", fencingToken, null, null, null);
    }

    private BatchEntity withQuality(BatchEntity batch, String status,
            String qualityStatus, Instant publishedAt) {
        return new BatchEntity(batch.id(), batch.tenantId(), batch.datasetId(),
                batch.syncRunId(), batch.importJobId(), batch.batchCode(), batch.generationId(),
                batch.publishSeq(), status, batch.expectedRows(), batch.receivedRows(),
                batch.validRows(), batch.rejectedRows(), batch.deduplicatedRows(),
                batch.sourceAmount(), batch.targetAmount(), batch.sourceBusinessKeys(),
                batch.targetBusinessKeys(), qualityStatus, batch.fencingToken(),
                batch.errorReport(), batch.readyAt(), publishedAt);
    }

    private SyncRunEntity withBatch(SyncRunEntity run, long batchId) {
        return new SyncRunEntity(run.id(), run.tenantId(), run.syncJobId(), run.attemptNo(),
                run.requestKey(), run.status(), run.leaseOwner(), run.leaseUntil(),
                run.fencingToken(), run.errorCode(), run.errorSummary(), run.retryCount(),
                run.deadLetter(), batchId, run.startedAt(), run.finishedAt(), run.createdAt());
    }

    private SyncRunEntity finish(SyncRunEntity run, String status, String code,
            String summary, boolean deadLetter) {
        return new SyncRunEntity(run.id(), run.tenantId(), run.syncJobId(), run.attemptNo(),
                run.requestKey(), status, run.leaseOwner(), run.leaseUntil(), run.fencingToken(),
                code, summary, run.retryCount(), deadLetter, run.batchId(), run.startedAt(),
                Instant.now(), run.createdAt());
    }

    private SyncRunEntity requiredRun(long tenantId, long runId) {
        return mapper.findRun(tenantId, runId)
                .orElseThrow(() -> new BadRequestException("同步运行不存在"));
    }

    private boolean retryable(RuntimeException exception) {
        return !(exception instanceof com.aibi.platform.common.exception.ForbiddenException)
                && !(exception instanceof com.aibi.platform.common.exception.UnauthorizedException)
                && !(exception instanceof BadRequestException)
                && !(exception instanceof ConflictException);
    }

    private String classify(RuntimeException exception) {
        if (exception instanceof com.aibi.platform.common.exception.ForbiddenException
                || exception instanceof com.aibi.platform.common.exception.UnauthorizedException) {
            return "AUTHORIZATION_REVOKED";
        }
        if (exception instanceof BadRequestException) {
            return "SOURCE_DATA_INVALID";
        }
        if (exception instanceof ConflictException) {
            return "STATE_CONFLICT";
        }
        return "SYNC_TEMPORARY_FAILURE";
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "同步执行失败" : message.substring(0,
                Math.min(message.length(), 500));
    }

    private long nextId() {
        return ids.incrementAndGet();
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }

    private record Extraction(long distinctEvents, long businessKeys, BigDecimal amount,
            long rejectedRows, long duplicates, String errorReport,
            Instant lastTime, String lastKey) {
    }
}
