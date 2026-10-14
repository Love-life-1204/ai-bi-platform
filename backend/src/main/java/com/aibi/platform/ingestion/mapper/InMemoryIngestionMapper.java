package com.aibi.platform.ingestion.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.ingestion.dto.IngestionDtos.IdempotentEventKey;
import com.aibi.platform.ingestion.dto.IngestionDtos.LeaseClaim;
import com.aibi.platform.ingestion.dto.IngestionDtos.PublicationData;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityResultData;
import com.aibi.platform.ingestion.entity.BatchEntity;
import com.aibi.platform.ingestion.entity.FileEntity;
import com.aibi.platform.ingestion.entity.ImportJobEntity;
import com.aibi.platform.ingestion.entity.QualityRuleEntity;
import com.aibi.platform.ingestion.entity.StagedRowEntity;
import com.aibi.platform.ingestion.entity.SyncJobEntity;
import com.aibi.platform.ingestion.entity.SyncRunEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryIngestionMapper implements IngestionMapper {

    private final Map<Long, FileEntity> files = new ConcurrentHashMap<>();
    private final Map<Long, ImportJobEntity> imports = new ConcurrentHashMap<>();
    private final Map<Long, SyncJobEntity> jobs = new ConcurrentHashMap<>();
    private final Map<Long, SyncRunEntity> runs = new ConcurrentHashMap<>();
    private final Map<Long, BatchEntity> batches = new ConcurrentHashMap<>();
    private final Map<IdempotentEventKey, StagedRowEntity> events = new ConcurrentHashMap<>();
    private final Map<Long, QualityRuleEntity> rules = new ConcurrentHashMap<>();
    private final Map<String, List<QualityResultData>> qualityResults = new ConcurrentHashMap<>();
    private final Map<String, PublicationData> publications = new ConcurrentHashMap<>();

    @Override
    public FileEntity insertFile(FileEntity file) {
        files.put(file.id(), file);
        return file;
    }

    @Override
    public Optional<FileEntity> findFile(long tenantId, long fileId) {
        return tenant(files.get(fileId), tenantId);
    }

    @Override
    public synchronized ImportJobEntity insertImport(ImportJobEntity job) {
        if (findImportByRequestKey(job.tenantId(), job.requestKey()).isPresent()) {
            throw new ConflictException("导入幂等键已存在");
        }
        imports.put(job.id(), job);
        return job;
    }

    @Override
    public Optional<ImportJobEntity> findImportByRequestKey(long tenantId, String requestKey) {
        return imports.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.requestKey().equals(requestKey))
                .findFirst();
    }

    @Override
    public Optional<ImportJobEntity> findImport(long tenantId, long importId) {
        return tenant(imports.get(importId), tenantId);
    }

    @Override
    public List<ImportJobEntity> listImports(long tenantId) {
        return imports.values().stream().filter(value -> value.tenantId() == tenantId)
                .sorted(Comparator.comparing(ImportJobEntity::createdAt).reversed()).toList();
    }

    @Override
    public ImportJobEntity updateImport(ImportJobEntity job) {
        imports.put(job.id(), job);
        return job;
    }

    @Override
    public synchronized SyncJobEntity insertSyncJob(SyncJobEntity job) {
        boolean duplicate = jobs.values().stream().anyMatch(value -> value.tenantId() == job.tenantId()
                && value.jobCode().equals(job.jobCode()));
        if (duplicate) {
            throw new ConflictException("同步任务编码已存在");
        }
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public synchronized SyncJobEntity updateSyncJob(SyncJobEntity job, long expectedVersion) {
        SyncJobEntity current = jobs.get(job.id());
        if (current == null || current.tenantId() != job.tenantId()) {
            throw new ConflictException("同步任务不存在");
        }
        if (current.lockVersion() != expectedVersion) {
            throw new ConflictException("同步任务已被其他请求修改");
        }
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public Optional<SyncJobEntity> findSyncJob(long tenantId, long jobId) {
        return tenant(jobs.get(jobId), tenantId);
    }

    @Override
    public List<SyncJobEntity> listSyncJobs(long tenantId) {
        return jobs.values().stream().filter(value -> value.tenantId() == tenantId)
                .sorted(Comparator.comparing(SyncJobEntity::createdAt).reversed()).toList();
    }

    @Override
    public synchronized SyncRunEntity insertRun(SyncRunEntity run) {
        if (findRunByRequestKey(run.tenantId(), run.requestKey()).isPresent()) {
            throw new ConflictException("同步运行幂等键已存在");
        }
        runs.put(run.id(), run);
        return run;
    }

    @Override
    public Optional<SyncRunEntity> findRunByRequestKey(long tenantId, String requestKey) {
        return runs.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.requestKey().equals(requestKey))
                .findFirst();
    }

    @Override
    public Optional<SyncRunEntity> findRun(long tenantId, long runId) {
        return tenant(runs.get(runId), tenantId);
    }

    @Override
    public List<SyncRunEntity> listRuns(long tenantId, Long jobId) {
        return runs.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> jobId == null || value.syncJobId() == jobId)
                .sorted(Comparator.comparing(SyncRunEntity::createdAt).reversed()).toList();
    }

    @Override
    public SyncRunEntity updateRun(SyncRunEntity run) {
        runs.put(run.id(), run);
        return run;
    }

    @Override
    public void recordCompensation(long tenantId, long failedRunId,
            long compensationRunId, String operator, String reason) {
        // 内存测试实现不单独保存审计表；补偿运行本身仍完整保留。
    }

    @Override
    public synchronized Optional<LeaseClaim> claimLease(long tenantId, long runId, String owner,
            Instant now, Duration duration) {
        SyncRunEntity run = runs.get(runId);
        if (run == null || run.tenantId() != tenantId || terminal(run.status())) {
            return Optional.empty();
        }
        if (run.leaseUntil() != null && run.leaseUntil().isAfter(now)
                && !owner.equals(run.leaseOwner())) {
            return Optional.empty();
        }
        long token = run.fencingToken() + 1;
        Instant expiresAt = now.plus(duration);
        runs.put(runId, new SyncRunEntity(run.id(), run.tenantId(), run.syncJobId(),
                run.attemptNo(), run.requestKey(), "RUNNING", owner, expiresAt, token,
                run.errorCode(), run.errorSummary(), run.retryCount(), run.deadLetter(),
                run.batchId(), run.startedAt() == null ? now : run.startedAt(),
                run.finishedAt(), run.createdAt()));
        return Optional.of(new LeaseClaim(runId, owner, token, expiresAt));
    }

    @Override
    public synchronized boolean renewLease(long tenantId, long runId, String owner,
            long fencingToken, Instant now, Duration duration) {
        SyncRunEntity run = runs.get(runId);
        if (run == null || run.tenantId() != tenantId || run.fencingToken() != fencingToken
                || !owner.equals(run.leaseOwner()) || terminal(run.status())) {
            return false;
        }
        runs.put(runId, new SyncRunEntity(run.id(), run.tenantId(), run.syncJobId(),
                run.attemptNo(), run.requestKey(), run.status(), owner, now.plus(duration),
                fencingToken, run.errorCode(), run.errorSummary(), run.retryCount(),
                run.deadLetter(), run.batchId(), run.startedAt(), run.finishedAt(), run.createdAt()));
        return true;
    }

    @Override
    public BatchEntity insertBatch(BatchEntity batch) {
        batches.put(batch.id(), batch);
        return batch;
    }

    @Override
    public BatchEntity updateBatch(BatchEntity batch) {
        batches.put(batch.id(), batch);
        return batch;
    }

    @Override
    public Optional<BatchEntity> findBatch(long tenantId, long batchId) {
        return tenant(batches.get(batchId), tenantId);
    }

    @Override
    public Optional<BatchEntity> findBatchByRun(long tenantId, long runId) {
        return batches.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.syncRunId() != null
                        && value.syncRunId() == runId)
                .findFirst();
    }

    @Override
    public boolean insertEvent(StagedRowEntity row) {
        return events.putIfAbsent(row.eventKey(), row) == null;
    }

    @Override
    public List<StagedRowEntity> listBatchRows(long tenantId, long batchId) {
        return events.values().stream().filter(value -> value.eventKey().tenantId() == tenantId)
                .filter(value -> value.batchId() == batchId).toList();
    }

    @Override
    public List<StagedRowEntity> listVisibleRows(long tenantId, long datasetId,
            long generationId, long committedPublishSeq) {
        return events.values().stream()
                .filter(value -> value.eventKey().tenantId() == tenantId)
                .filter(value -> value.eventKey().datasetId() == datasetId)
                .filter(value -> value.eventKey().generationId() == generationId)
                .filter(value -> value.eventKey().publishSeq() <= committedPublishSeq)
                .toList();
    }

    @Override
    public synchronized QualityRuleEntity insertQualityRule(QualityRuleEntity rule) {
        boolean duplicate = rules.values().stream().anyMatch(value -> value.tenantId() == rule.tenantId()
                && value.datasetId() == rule.datasetId() && value.ruleCode().equals(rule.ruleCode()));
        if (duplicate) {
            throw new ConflictException("质量规则编码已存在");
        }
        rules.put(rule.id(), rule);
        return rule;
    }

    @Override
    public List<QualityRuleEntity> listQualityRules(long tenantId, Long datasetId) {
        return rules.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> datasetId == null || value.datasetId() == datasetId)
                .sorted(Comparator.comparing(QualityRuleEntity::createdAt)).toList();
    }

    @Override
    public void saveQualityResults(long tenantId, long batchId, List<QualityResultData> results) {
        qualityResults.put(key(tenantId, batchId), List.copyOf(results));
    }

    @Override
    public List<QualityResultData> listQualityResults(long tenantId, long batchId) {
        return qualityResults.getOrDefault(key(tenantId, batchId), List.of());
    }

    @Override
    public PublicationData publication(long tenantId, long datasetId) {
        return publications.computeIfAbsent(key(tenantId, datasetId), ignored ->
                new PublicationData(datasetId, null, 0, 0, null, 0));
    }

    @Override
    public synchronized PublicationData publishCas(long tenantId, long datasetId,
            long expectedVersion, long generationId, long publishSeq,
            long qualityVersion, long batchId) {
        String key = key(tenantId, datasetId);
        PublicationData current = publication(tenantId, datasetId);
        if (current.lockVersion() != expectedVersion) {
            throw new ConflictException("发布指针已变化，请按同一批次重试");
        }
        PublicationData updated = new PublicationData(datasetId, generationId, publishSeq,
                qualityVersion, batchId, current.lockVersion() + 1);
        publications.put(key, updated);
        return updated;
    }

    @Override
    public synchronized PublicationData publishSyncCas(SyncJobEntity updatedJob,
            long expectedJobVersion, BatchEntity publishedBatch,
            long expectedPublicationVersion, long qualityVersion) {
        SyncJobEntity currentJob = jobs.get(updatedJob.id());
        PublicationData currentPublication = publication(updatedJob.tenantId(), updatedJob.datasetId());
        if (currentJob == null || currentJob.tenantId() != updatedJob.tenantId()
                || currentJob.lockVersion() != expectedJobVersion) {
            throw new ConflictException("同步任务已被其他请求修改");
        }
        if (currentPublication.lockVersion() != expectedPublicationVersion) {
            throw new ConflictException("发布指针已变化，请按同一批次重试");
        }
        PublicationData updated = new PublicationData(updatedJob.datasetId(),
                publishedBatch.generationId(), publishedBatch.publishSeq(), qualityVersion,
                publishedBatch.id(), currentPublication.lockVersion() + 1);
        publications.put(key(updatedJob.tenantId(), updatedJob.datasetId()), updated);
        jobs.put(updatedJob.id(), updatedJob);
        batches.put(publishedBatch.id(), publishedBatch);
        return updated;
    }

    private boolean terminal(String status) {
        return List.of("SUCCESS", "PARTIAL_SUCCESS", "FAILED", "CANCELLED", "TIMEOUT")
                .contains(status);
    }

    private <T> Optional<T> tenant(T value, long tenantId) {
        if (value instanceof FileEntity file && file.tenantId() == tenantId
                || value instanceof ImportJobEntity importJob && importJob.tenantId() == tenantId
                || value instanceof SyncJobEntity syncJob && syncJob.tenantId() == tenantId
                || value instanceof SyncRunEntity syncRun && syncRun.tenantId() == tenantId
                || value instanceof BatchEntity batch && batch.tenantId() == tenantId) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    private String key(long first, long second) {
        return first + ":" + second;
    }
}
