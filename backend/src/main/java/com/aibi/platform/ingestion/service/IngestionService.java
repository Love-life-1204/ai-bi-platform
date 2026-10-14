package com.aibi.platform.ingestion.service;

import com.aibi.platform.ingestion.dto.IngestionDtos.CompensationRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.CreateImportRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.CreateSyncJobRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.FileData;
import com.aibi.platform.ingestion.dto.IngestionDtos.FilePreviewData;
import com.aibi.platform.ingestion.dto.IngestionDtos.ImportJobData;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRuleData;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRuleRequest;
import com.aibi.platform.ingestion.dto.IngestionDtos.QualityRunData;
import com.aibi.platform.ingestion.dto.IngestionDtos.SyncJobData;
import com.aibi.platform.ingestion.dto.IngestionDtos.SyncRunData;
import com.aibi.platform.ingestion.dto.IngestionDtos.TaskAccepted;
import com.aibi.platform.ingestion.dto.IngestionDtos.UpdateSyncJobRequest;
import com.aibi.platform.ingestion.entity.FileEntity;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface IngestionService {

    FileData upload(String authorization, long tenantId, Long workspaceId, MultipartFile file);

    FilePreviewData preview(String authorization, long tenantId, long fileId);

    FileEntity file(String authorization, long tenantId, long fileId);

    ImportJobData createImport(String authorization, long tenantId, String idempotencyKey, CreateImportRequest request);

    List<ImportJobData> listImports(String authorization, long tenantId);

    ImportJobData importStatus(String authorization, long tenantId, long importId);

    ImportJobData cancelImport(String authorization, long tenantId, long importId);

    SyncJobData createSyncJob(String authorization, long tenantId, CreateSyncJobRequest request);

    SyncJobData updateSyncJob(String authorization, long tenantId, long jobId, UpdateSyncJobRequest request);

    SyncJobData setSyncJobEnabled(String authorization, long tenantId, long jobId, boolean enabled, long lockVersion);

    List<SyncJobData> listSyncJobs(String authorization, long tenantId);

    SyncJobData syncJob(String authorization, long tenantId, long jobId);

    TaskAccepted runSync(String authorization, long tenantId, long jobId, String idempotencyKey);

    List<SyncRunData> listRuns(String authorization, long tenantId, Long jobId);

    SyncRunData syncRun(String authorization, long tenantId, long runId);

    SyncRunData cancelRun(String authorization, long tenantId, long runId);

    TaskAccepted compensate(String authorization, long tenantId, long runId, CompensationRequest request);

    QualityRuleData createQualityRule(String authorization, long tenantId, QualityRuleRequest request);

    List<QualityRuleData> listQualityRules(String authorization, long tenantId, Long datasetId);

    QualityRunData runQuality(String authorization, long tenantId, long batchId);
}
