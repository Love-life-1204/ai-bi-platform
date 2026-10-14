package com.aibi.platform.ingestion.mapper;

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
import java.util.List;
import java.util.Optional;

public interface IngestionMapper {

    FileEntity insertFile(FileEntity file);

    Optional<FileEntity> findFile(long tenantId, long fileId);

    ImportJobEntity insertImport(ImportJobEntity job);

    Optional<ImportJobEntity> findImportByRequestKey(long tenantId, String requestKey);

    Optional<ImportJobEntity> findImport(long tenantId, long importId);

    List<ImportJobEntity> listImports(long tenantId);

    ImportJobEntity updateImport(ImportJobEntity job);

    SyncJobEntity insertSyncJob(SyncJobEntity job);

    SyncJobEntity updateSyncJob(SyncJobEntity job, long expectedVersion);

    Optional<SyncJobEntity> findSyncJob(long tenantId, long jobId);

    List<SyncJobEntity> listSyncJobs(long tenantId);

    SyncRunEntity insertRun(SyncRunEntity run);

    Optional<SyncRunEntity> findRunByRequestKey(long tenantId, String requestKey);

    Optional<SyncRunEntity> findRun(long tenantId, long runId);

    List<SyncRunEntity> listRuns(long tenantId, Long jobId);

    SyncRunEntity updateRun(SyncRunEntity run);

    void recordCompensation(long tenantId, long failedRunId, long compensationRunId,
            String operator, String reason);

    Optional<LeaseClaim> claimLease(long tenantId, long runId, String owner,
            Instant now, Duration duration);

    boolean renewLease(long tenantId, long runId, String owner, long fencingToken,
            Instant now, Duration duration);

    BatchEntity insertBatch(BatchEntity batch);

    BatchEntity updateBatch(BatchEntity batch);

    Optional<BatchEntity> findBatch(long tenantId, long batchId);

    Optional<BatchEntity> findBatchByRun(long tenantId, long runId);

    boolean insertEvent(StagedRowEntity row);

    List<StagedRowEntity> listBatchRows(long tenantId, long batchId);

    List<StagedRowEntity> listVisibleRows(long tenantId, long datasetId,
            long generationId, long committedPublishSeq);

    QualityRuleEntity insertQualityRule(QualityRuleEntity rule);

    List<QualityRuleEntity> listQualityRules(long tenantId, Long datasetId);

    void saveQualityResults(long tenantId, long batchId, List<QualityResultData> results);

    List<QualityResultData> listQualityResults(long tenantId, long batchId);

    PublicationData publication(long tenantId, long datasetId);

    PublicationData publishCas(long tenantId, long datasetId, long expectedVersion,
            long generationId, long publishSeq, long qualityVersion, long batchId);

    PublicationData publishSyncCas(SyncJobEntity updatedJob, long expectedJobVersion,
            BatchEntity publishedBatch, long expectedPublicationVersion, long qualityVersion);
}
