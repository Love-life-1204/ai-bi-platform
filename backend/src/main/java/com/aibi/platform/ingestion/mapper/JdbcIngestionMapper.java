package com.aibi.platform.ingestion.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.ingestion.dto.IngestionDtos.FieldMapping;
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
import com.aibi.platform.ingestion.manager.IngestionProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcIngestionMapper implements IngestionMapper {

    private static final TypeReference<List<FieldMapping>> MAPPINGS = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> VALUE_MAP = new TypeReference<>() { };
    private final NamedParameterJdbcTemplate mysql;
    private final JdbcTemplate clickhouse;
    private final ObjectMapper json;
    private final Path storageRoot;
    private final TransactionTemplate transactions;

    public JdbcIngestionMapper(
            @Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate mysql,
            @Qualifier("clickHouseWriterJdbcTemplate") JdbcTemplate clickhouse,
            @Qualifier("mysqlTransactionManager") PlatformTransactionManager transactionManager,
            ObjectMapper json, IngestionProperties properties) {
        this.mysql = mysql;
        this.clickhouse = clickhouse;
        this.json = json;
        this.storageRoot = properties.getStorageDir().toAbsolutePath().normalize();
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public FileEntity insertFile(FileEntity file) {
        String objectKey = relativeObjectKey(file);
        mysql.update("""
                INSERT INTO sys_object_file
                    (id,tenant_id,workspace_id,owner_member_id,object_key,bucket_name,file_name,content_type,size_bytes,
                     content_hash,purpose,file_status,created_at,updated_at,lock_version)
                VALUES (:id,:tenantId,:workspaceId,:ownerMemberId,:objectKey,'local-ingestion',:fileName,:contentType,:sizeBytes,
                        :hash,'IMPORT',:status,:createdAt,:createdAt,0)
                """, new MapSqlParameterSource()
                .addValue("id", file.id()).addValue("tenantId", file.tenantId())
                .addValue("workspaceId", file.workspaceId()).addValue("ownerMemberId", file.ownerMemberId())
                .addValue("objectKey", objectKey).addValue("fileName", file.fileName())
                .addValue("contentType", file.contentType()).addValue("sizeBytes", file.sizeBytes())
                .addValue("hash", file.sha256()).addValue("status", file.status())
                .addValue("createdAt", timestamp(file.createdAt())));
        return file;
    }

    @Override
    public Optional<FileEntity> findFile(long tenantId, long fileId) {
        return mysql.query("""
                SELECT id,tenant_id,workspace_id,owner_member_id,object_key,file_name,content_type,size_bytes,content_hash,
                       file_status,created_at FROM sys_object_file
                 WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "id", fileId), this::file).stream().findFirst();
    }

    @Override
    public ImportJobEntity insertImport(ImportJobEntity job) {
        try {
            mysql.update("""
                    INSERT INTO bi_import_job
                        (id,tenant_id,workspace_id,dataset_id,object_file_id,requester_member_id,
                         request_key,mapping_json,strict_mode,import_status,total_rows,valid_rows,
                         error_rows,error_object_id,error_summary,created_at,updated_at,lock_version)
                    VALUES (:id,:tenantId,:workspaceId,:datasetId,:fileId,:memberId,:requestKey,
                            :mappings,:strictMode,:status,:totalRows,:validRows,:errorRows,
                            :errorFileId,:errorSummary,:createdAt,:updatedAt,0)
                    """, importParameters(job));
            return job;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("导入幂等键已存在");
        }
    }

    @Override
    public Optional<ImportJobEntity> findImportByRequestKey(long tenantId, String requestKey) {
        return imports(" AND request_key=:requestKey", new MapSqlParameterSource("tenantId", tenantId)
                .addValue("requestKey", requestKey)).stream().findFirst();
    }

    @Override
    public Optional<ImportJobEntity> findImport(long tenantId, long importId) {
        return imports(" AND id=:id", new MapSqlParameterSource("tenantId", tenantId)
                .addValue("id", importId)).stream().findFirst();
    }

    @Override
    public List<ImportJobEntity> listImports(long tenantId) {
        return imports(" ORDER BY created_at DESC", new MapSqlParameterSource("tenantId", tenantId));
    }

    @Override
    public ImportJobEntity updateImport(ImportJobEntity job) {
        mysql.update("""
                UPDATE bi_import_job SET import_status=:status,total_rows=:totalRows,
                       valid_rows=:validRows,error_rows=:errorRows,error_object_id=:errorFileId,
                       error_summary=:errorSummary,updated_at=:updatedAt,lock_version=lock_version+1
                 WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL
                """, importParameters(job));
        return job;
    }

    @Override
    public SyncJobEntity insertSyncJob(SyncJobEntity job) {
        try {
            mysql.update("""
                    INSERT INTO bi_sync_job
                        (id,tenant_id,datasource_id,dataset_id,job_code,sync_mode,schedule_expression,
                         timezone,config_json,job_status,created_at,updated_at,lock_version)
                    VALUES (:id,:tenantId,:datasourceId,:datasetId,:jobCode,:mode,:schedule,
                            :timezone,:config,:status,:createdAt,:updatedAt,:lockVersion)
                    """, syncJobParameters(job));
            saveWatermark(job);
            return job;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("同步任务编码已存在");
        }
    }

    @Override
    @MySqlTransactional
    public SyncJobEntity updateSyncJob(SyncJobEntity job, long expectedVersion) {
        int updated = mysql.update("""
                UPDATE bi_sync_job SET sync_mode=:mode,schedule_expression=:schedule,
                       timezone=:timezone,config_json=:config,job_status=:status,
                       updated_at=:updatedAt,lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expectedVersion
                   AND deleted_at IS NULL
                """, syncJobParameters(job).addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new ConflictException("同步任务已被其他请求修改");
        }
        saveWatermark(job);
        return job;
    }

    @Override
    public Optional<SyncJobEntity> findSyncJob(long tenantId, long jobId) {
        return syncJobs(" AND j.id=:id", new MapSqlParameterSource("tenantId", tenantId)
                .addValue("id", jobId)).stream().findFirst();
    }

    @Override
    public List<SyncJobEntity> listSyncJobs(long tenantId) {
        return syncJobs(" ORDER BY j.created_at DESC", new MapSqlParameterSource("tenantId", tenantId));
    }

    @Override
    public SyncRunEntity insertRun(SyncRunEntity run) {
        try {
            mysql.update("""
                    INSERT INTO bi_sync_run
                        (id,tenant_id,sync_job_id,attempt_no,request_key,run_status,lease_owner,
                         lease_until,fencing_token,started_at,finished_at,error_code,error_summary,
                         retry_count,dead_letter,created_at,updated_at,lock_version)
                    VALUES (:id,:tenantId,:jobId,:attempt,:requestKey,:status,:leaseOwner,
                            :leaseUntil,:token,:startedAt,:finishedAt,:errorCode,:errorSummary,
                            :retryCount,:deadLetter,:createdAt,:createdAt,0)
                    """, runParameters(run));
            return run;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("同步运行幂等键已存在");
        }
    }

    @Override
    public Optional<SyncRunEntity> findRunByRequestKey(long tenantId, String requestKey) {
        return runs(" AND r.request_key=:requestKey", new MapSqlParameterSource("tenantId", tenantId)
                .addValue("requestKey", requestKey)).stream().findFirst();
    }

    @Override
    public Optional<SyncRunEntity> findRun(long tenantId, long runId) {
        return runs(" AND r.id=:id", new MapSqlParameterSource("tenantId", tenantId)
                .addValue("id", runId)).stream().findFirst();
    }

    @Override
    public List<SyncRunEntity> listRuns(long tenantId, Long jobId) {
        String filter = jobId == null ? "" : " AND r.sync_job_id=:jobId";
        return runs(filter + " ORDER BY r.created_at DESC", new MapSqlParameterSource("tenantId", tenantId)
                .addValue("jobId", jobId));
    }

    @Override
    public SyncRunEntity updateRun(SyncRunEntity run) {
        mysql.update("""
                UPDATE bi_sync_run SET run_status=:status,lease_owner=:leaseOwner,
                       lease_until=:leaseUntil,fencing_token=:token,started_at=:startedAt,
                       finished_at=:finishedAt,error_code=:errorCode,error_summary=:errorSummary,
                       retry_count=:retryCount,dead_letter=:deadLetter,updated_at=NOW(3),
                       lock_version=lock_version+1
                 WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL
                """, runParameters(run));
        if (run.deadLetter()) {
            saveDeadLetter(run);
        }
        return run;
    }

    @Override
    public void recordCompensation(long tenantId, long failedRunId,
            long compensationRunId, String operator, String reason) {
        int updated = mysql.update("""
                UPDATE bi_sync_dead_letter SET dead_status='COMPENSATED',compensated_by=:operator,
                       compensation_reason=:reason,compensated_run_id=:compensationRunId,
                       updated_at=NOW(3)
                 WHERE tenant_id=:tenantId AND sync_run_id=:failedRunId AND dead_status='OPEN'
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                .addValue("failedRunId", failedRunId).addValue("compensationRunId", compensationRunId)
                .addValue("operator", operator).addValue("reason", reason));
        if (updated != 1) {
            throw new ConflictException("死信不存在或已完成补偿");
        }
    }

    @Override
    @MySqlTransactional
    public Optional<LeaseClaim> claimLease(long tenantId, long runId, String owner,
            Instant now, Duration duration) {
        Instant until = now.plus(duration);
        int updated = mysql.update("""
                UPDATE bi_sync_run SET run_status='RUNNING',lease_owner=:owner,lease_until=:until,
                       fencing_token=fencing_token+1,started_at=COALESCE(started_at,:now),
                       updated_at=:now,lock_version=lock_version+1
                 WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL
                   AND run_status IN ('PENDING','RUNNING')
                   AND (lease_until IS NULL OR lease_until<=:now OR lease_owner=:owner)
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                .addValue("id", runId).addValue("owner", owner).addValue("now", timestamp(now))
                .addValue("until", timestamp(until)));
        if (updated != 1) {
            return Optional.empty();
        }
        SyncRunEntity run = findRun(tenantId, runId).orElseThrow();
        return Optional.of(new LeaseClaim(runId, owner, run.fencingToken(), until));
    }

    @Override
    public boolean renewLease(long tenantId, long runId, String owner, long fencingToken,
            Instant now, Duration duration) {
        return mysql.update("""
                UPDATE bi_sync_run SET lease_until=:until,updated_at=:now
                 WHERE tenant_id=:tenantId AND id=:id AND run_status='RUNNING'
                   AND lease_owner=:owner AND fencing_token=:token AND deleted_at IS NULL
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                .addValue("id", runId).addValue("owner", owner).addValue("token", fencingToken)
                .addValue("now", timestamp(now)).addValue("until", timestamp(now.plus(duration)))) == 1;
    }

    @Override
    public BatchEntity insertBatch(BatchEntity batch) {
        mysql.update("""
                INSERT INTO bi_batch
                    (id,tenant_id,dataset_id,sync_run_id,import_job_id,batch_code,generation_id,
                     publish_seq,batch_status,expected_rows,actual_rows,received_rows,valid_rows,
                     rejected_rows,deduplicated_rows,source_amount,target_amount,source_business_keys,
                     target_business_keys,quality_status,fencing_token,error_report,ready_at,published_at,
                     created_at,updated_at,lock_version)
                VALUES (:id,:tenantId,:datasetId,:runId,:importId,:code,:generation,:publishSeq,
                        :status,:expected,:received,:received,:valid,:rejected,:deduplicated,
                        :sourceAmount,:targetAmount,:sourceKeys,:targetKeys,:quality,:token,:errorReport,
                        :readyAt,:publishedAt,NOW(3),NOW(3),0)
                """, batchParameters(batch));
        return batch;
    }

    @Override
    public BatchEntity updateBatch(BatchEntity batch) {
        mysql.update("""
                UPDATE bi_batch SET batch_status=:status,expected_rows=:expected,
                       actual_rows=:received,received_rows=:received,valid_rows=:valid,
                       rejected_rows=:rejected,deduplicated_rows=:deduplicated,
                       source_amount=:sourceAmount,target_amount=:targetAmount,
                       source_business_keys=:sourceKeys,target_business_keys=:targetKeys,
                       quality_status=:quality,fencing_token=:token,error_report=:errorReport,
                       ready_at=:readyAt,published_at=:publishedAt,updated_at=NOW(3),
                       lock_version=lock_version+1
                 WHERE tenant_id=:tenantId AND id=:id AND deleted_at IS NULL
                """, batchParameters(batch));
        return batch;
    }

    @Override
    public Optional<BatchEntity> findBatch(long tenantId, long batchId) {
        return batches(" AND id=:id", new MapSqlParameterSource("tenantId", tenantId)
                .addValue("id", batchId)).stream().findFirst();
    }

    @Override
    public Optional<BatchEntity> findBatchByRun(long tenantId, long runId) {
        return batches(" AND sync_run_id=:runId", new MapSqlParameterSource("tenantId", tenantId)
                .addValue("runId", runId)).stream().findFirst();
    }

    @Override
    public boolean insertEvent(StagedRowEntity row) {
        Boolean claimed = transactions.execute(status -> {
            boolean created = claimEvent(row);
            if (created) {
                insertStaged(row);
            }
            return created;
        });
        boolean created = Boolean.TRUE.equals(claimed);
        String status = stagedWriteStatus(row.eventKey()).orElse(null);
        if ("PENDING".equals(status)) {
            writeClickHouse(row);
            mysql.update("""
                    UPDATE bi_sync_staged_row SET write_status='WRITTEN',updated_at=NOW(3)
                     WHERE tenant_id=:tenantId AND dataset_id=:datasetId
                       AND source_id=:sourceId AND generation_id=:generation
                       AND publish_seq=:publishSeq
                       AND business_key=:businessKey AND source_version=:sourceVersion
                    """, eventParameters(row));
        }
        return created;
    }

    @Override
    public List<StagedRowEntity> listBatchRows(long tenantId, long batchId) {
        return staged(" AND batch_id=:batchId", new MapSqlParameterSource("tenantId", tenantId)
                .addValue("batchId", batchId));
    }

    @Override
    public List<StagedRowEntity> listVisibleRows(long tenantId, long datasetId,
            long generationId, long committedPublishSeq) {
        return staged(" AND dataset_id=:datasetId AND generation_id=:generation"
                + " AND publish_seq<=:publishSeq", new MapSqlParameterSource("tenantId", tenantId)
                .addValue("datasetId", datasetId).addValue("generation", generationId)
                .addValue("publishSeq", committedPublishSeq));
    }

    @Override
    public QualityRuleEntity insertQualityRule(QualityRuleEntity rule) {
        try {
            mysql.update("""
                    INSERT INTO bi_quality_rule
                        (id,tenant_id,dataset_id,rule_code,rule_type,rule_config,severity,
                         rule_status,created_at,updated_at,lock_version)
                    VALUES (:id,:tenantId,:datasetId,:code,:type,:config,:severity,
                            :status,:createdAt,:createdAt,0)
                    """, qualityRuleParameters(rule));
            return rule;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("质量规则编码已存在");
        }
    }

    @Override
    public List<QualityRuleEntity> listQualityRules(long tenantId, Long datasetId) {
        String filter = datasetId == null ? "" : " AND dataset_id=:datasetId";
        String sql = """
                SELECT id,tenant_id,dataset_id,rule_code,rule_type,rule_config,severity,
                       rule_status,created_at FROM bi_quality_rule
                 WHERE tenant_id=:tenantId AND deleted_at IS NULL
                """ + filter + " ORDER BY created_at";
        return mysql.query(sql, new MapSqlParameterSource("tenantId", tenantId)
                .addValue("datasetId", datasetId), this::qualityRule);
    }

    @Override
    @MySqlTransactional
    public void saveQualityResults(long tenantId, long batchId,
            List<QualityResultData> results) {
        mysql.update("DELETE FROM bi_quality_result WHERE tenant_id=:tenantId AND batch_id=:batchId",
                Map.of("tenantId", tenantId, "batchId", batchId));
        for (QualityResultData result : results) {
            mysql.update("""
                    INSERT INTO bi_quality_result
                        (id,tenant_id,batch_id,quality_rule_id,system_rule_code,result_status,
                         observed_value,expected_value,checked_at,created_at,updated_at,lock_version)
                    VALUES (:id,:tenantId,:batchId,:ruleId,:systemCode,:status,
                            :observed,:expected,NOW(3),NOW(3),NOW(3),0)
                    """, new MapSqlParameterSource().addValue("id", newId())
                    .addValue("tenantId", tenantId).addValue("batchId", batchId)
                    .addValue("ruleId", result.ruleId() == 0 ? null : result.ruleId())
                    .addValue("systemCode", result.ruleId() == 0 ? result.ruleCode() : null)
                    .addValue("status", result.status()).addValue("observed", json(result.observedValue()))
                    .addValue("expected", json(result.expectedValue())));
        }
    }

    @Override
    public List<QualityResultData> listQualityResults(long tenantId, long batchId) {
        return mysql.query("""
                SELECT r.quality_rule_id,r.system_rule_code,r.result_status,r.observed_value,
                       r.expected_value,q.rule_code
                  FROM bi_quality_result r
                  LEFT JOIN bi_quality_rule q ON q.tenant_id=r.tenant_id AND q.id=r.quality_rule_id
                 WHERE r.tenant_id=:tenantId AND r.batch_id=:batchId AND r.deleted_at IS NULL
                 ORDER BY r.id
                """, Map.of("tenantId", tenantId, "batchId", batchId), (result, row) -> {
            long ruleId = result.getLong("quality_rule_id");
            String code = ruleId == 0 ? result.getString("system_rule_code") : result.getString("rule_code");
            return new QualityResultData(ruleId, code, result.getString("result_status"),
                    parseLongJson(result.getString("observed_value")),
                    parseStringJson(result.getString("observed_value")),
                    parseStringJson(result.getString("expected_value")));
        });
    }

    @Override
    @MySqlTransactional
    public PublicationData publication(long tenantId, long datasetId) {
        mysql.update("""
                INSERT INTO bi_dataset_publication
                    (id,tenant_id,dataset_id,active_generation,committed_publish_seq,
                     data_quality_version,created_at,updated_at,lock_version)
                VALUES (:id,:tenantId,:datasetId,NULL,0,0,NOW(3),NOW(3),0)
                ON DUPLICATE KEY UPDATE id=id
                """, Map.of("id", newId(), "tenantId", tenantId, "datasetId", datasetId));
        return selectPublication(tenantId, datasetId);
    }

    @Override
    @MySqlTransactional
    public PublicationData publishCas(long tenantId, long datasetId, long expectedVersion,
            long generationId, long publishSeq, long qualityVersion, long batchId) {
        int updated = mysql.update("""
                UPDATE bi_dataset_publication
                   SET active_generation=:generation,committed_publish_seq=:publishSeq,
                       data_quality_version=:qualityVersion,last_batch_id=:batchId,
                       published_at=NOW(3),updated_at=NOW(3),lock_version=lock_version+1
                 WHERE tenant_id=:tenantId AND dataset_id=:datasetId
                   AND lock_version=:expectedVersion AND deleted_at IS NULL
                """, new MapSqlParameterSource().addValue("tenantId", tenantId)
                .addValue("datasetId", datasetId).addValue("expectedVersion", expectedVersion)
                .addValue("generation", generationId).addValue("publishSeq", publishSeq)
                .addValue("qualityVersion", qualityVersion).addValue("batchId", batchId));
        if (updated != 1) {
            throw new ConflictException("发布指针已变化，请按同一批次重试");
        }
        return selectPublication(tenantId, datasetId);
    }

    @Override
    public PublicationData publishSyncCas(SyncJobEntity updatedJob, long expectedJobVersion,
            BatchEntity publishedBatch, long expectedPublicationVersion, long qualityVersion) {
        return transactions.execute(status -> {
            PublicationData publication = publishCas(updatedJob.tenantId(), updatedJob.datasetId(),
                    expectedPublicationVersion, publishedBatch.generationId(),
                    publishedBatch.publishSeq(), qualityVersion, publishedBatch.id());
            updateBatch(publishedBatch);
            updateSyncJob(updatedJob, expectedJobVersion);
            return publication;
        });
    }

    private List<ImportJobEntity> imports(String suffix, MapSqlParameterSource parameters) {
        return mysql.query("""
                SELECT id,tenant_id,workspace_id,dataset_id,requester_member_id,object_file_id,
                       request_key,mapping_json,strict_mode,import_status,total_rows,valid_rows,
                       error_rows,error_object_id,error_summary,created_at,updated_at
                  FROM bi_import_job WHERE tenant_id=:tenantId AND deleted_at IS NULL
                """ + suffix, parameters, this::importJob);
    }

    private List<SyncJobEntity> syncJobs(String suffix, MapSqlParameterSource parameters) {
        return mysql.query("""
                SELECT j.id,j.tenant_id,j.datasource_id,j.dataset_id,j.job_code,j.sync_mode,
                       j.schedule_expression,j.timezone,j.config_json,j.job_status,j.lock_version,
                       j.created_at,j.updated_at,w.last_updated_at,w.last_primary_key
                  FROM bi_sync_job j LEFT JOIN bi_sync_watermark w
                    ON w.tenant_id=j.tenant_id AND w.sync_job_id=j.id AND w.deleted_at IS NULL
                 WHERE j.tenant_id=:tenantId AND j.deleted_at IS NULL
                """ + suffix, parameters, this::syncJob);
    }

    private List<SyncRunEntity> runs(String suffix, MapSqlParameterSource parameters) {
        return mysql.query("""
                SELECT r.*,b.id AS batch_id FROM bi_sync_run r LEFT JOIN bi_batch b
                  ON b.tenant_id=r.tenant_id AND b.sync_run_id=r.id AND b.deleted_at IS NULL
                 WHERE r.tenant_id=:tenantId AND r.deleted_at IS NULL
                """ + suffix, parameters, this::syncRun);
    }

    private List<BatchEntity> batches(String suffix, MapSqlParameterSource parameters) {
        return mysql.query("""
                SELECT * FROM bi_batch WHERE tenant_id=:tenantId AND deleted_at IS NULL
                """ + suffix, parameters, this::batch);
    }

    private List<StagedRowEntity> staged(String suffix, MapSqlParameterSource parameters) {
        return mysql.query("""
                SELECT * FROM bi_sync_staged_row WHERE tenant_id=:tenantId AND write_status='WRITTEN'
                """ + suffix, parameters, this::stagedRow);
    }

    private boolean claimEvent(StagedRowEntity row) {
        try {
            mysql.update("""
                    INSERT INTO bi_sync_event_dedup
                        (id,tenant_id,dataset_id,source_id,generation_id,publish_seq,business_key_hash,
                         source_version_hash,batch_id,fencing_token)
                    VALUES (:id,:tenantId,:datasetId,:sourceId,:generation,:publishSeq,:businessHash,
                            :versionHash,:batchId,:token)
                    """, eventParameters(row).addValue("id", newId())
                    .addValue("businessHash", sha256(row.eventKey().businessKey()))
                    .addValue("versionHash", sha256(row.eventKey().sourceVersion())));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private void insertStaged(StagedRowEntity row) {
        mysql.update("""
                INSERT INTO bi_sync_staged_row
                    (id,tenant_id,dataset_id,source_id,generation_id,publish_seq,business_key,source_version,
                     batch_id,fencing_token,values_json,amount,source_updated_at,is_deleted,write_status)
                VALUES (:id,:tenantId,:datasetId,:sourceId,:generation,:publishSeq,:businessKey,:sourceVersion,
                        :batchId,:token,:values,:amount,:updatedAt,:deleted,'PENDING')
                """, eventParameters(row).addValue("id", newId())
                .addValue("values", json(row.values())).addValue("amount", row.amount())
                .addValue("updatedAt", timestamp(row.updatedAt())).addValue("deleted", row.deleted()));
    }

    private Optional<String> stagedWriteStatus(IdempotentEventKey key) {
        List<String> values = mysql.queryForList("""
                SELECT write_status FROM bi_sync_staged_row
                 WHERE tenant_id=:tenantId AND dataset_id=:datasetId AND source_id=:sourceId
                   AND generation_id=:generation
                   AND publish_seq=:publishSeq AND business_key=:businessKey
                   AND source_version=:sourceVersion ORDER BY id DESC LIMIT 1
                """, eventKeyParameters(key), String.class);
        return values.stream().findFirst();
    }

    void writeClickHouse(StagedRowEntity row) {
        String payload = json(row.values());
        String rowEventKey = sha256(row.eventKey().businessKey() + "|" + row.eventKey().sourceVersion());
        clickhouse.update("""
                INSERT INTO ai_bi_raw.ingest_event
                    (tenant_id,dataset_id,source_id,business_key,generation_id,publish_seq,batch_id,
                     row_event_key,source_version,is_deleted,row_hash,payload_json,ingested_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,now64(3))
                """, row.eventKey().tenantId(), row.eventKey().datasetId(), row.eventKey().sourceId(),
                row.eventKey().businessKey(), row.eventKey().generationId(),
                row.eventKey().publishSeq(), row.batchId(), rowEventKey,
                positiveVersion(row.eventKey().sourceVersion()), row.deleted() ? 1 : 0,
                sha256(payload), payload);
    }

    private void saveWatermark(SyncJobEntity job) {
        mysql.update("""
                INSERT INTO bi_sync_watermark
                    (id,tenant_id,sync_job_id,last_updated_at,last_primary_key,
                     created_at,updated_at,lock_version)
                VALUES (:id,:tenantId,:jobId,:lastTime,:lastKey,NOW(3),NOW(3),0)
                ON DUPLICATE KEY UPDATE last_updated_at=VALUES(last_updated_at),
                    last_primary_key=VALUES(last_primary_key),updated_at=NOW(3),lock_version=lock_version+1
                """, new MapSqlParameterSource().addValue("id", newId())
                .addValue("tenantId", job.tenantId()).addValue("jobId", job.id())
                .addValue("lastTime", timestamp(job.watermarkTime()))
                .addValue("lastKey", job.watermarkPrimaryKey()));
    }

    private void saveDeadLetter(SyncRunEntity run) {
        mysql.update("""
                INSERT INTO bi_sync_dead_letter
                    (id,tenant_id,sync_run_id,error_code,error_summary,payload_json,dead_status)
                VALUES (:id,:tenantId,:runId,:code,:summary,:payload,'OPEN')
                ON DUPLICATE KEY UPDATE error_code=VALUES(error_code),
                    error_summary=VALUES(error_summary),payload_json=VALUES(payload_json),updated_at=NOW(3)
                """, new MapSqlParameterSource().addValue("id", newId())
                .addValue("tenantId", run.tenantId()).addValue("runId", run.id())
                .addValue("code", run.errorCode() == null ? "SYNC_FAILED" : run.errorCode())
                .addValue("summary", run.errorSummary() == null ? "同步失败" : run.errorSummary())
                .addValue("payload", json(Map.of("jobId", run.syncJobId(),
                        "attempt", run.attemptNo(), "requestKey", run.requestKey()))));
    }

    private PublicationData selectPublication(long tenantId, long datasetId) {
        return mysql.queryForObject("""
                SELECT dataset_id,active_generation,committed_publish_seq,data_quality_version,
                       last_batch_id,lock_version FROM bi_dataset_publication
                 WHERE tenant_id=:tenantId AND dataset_id=:datasetId AND deleted_at IS NULL
                """, Map.of("tenantId", tenantId, "datasetId", datasetId), (result, row) ->
                new PublicationData(result.getLong("dataset_id"), nullableLong(result, "active_generation"),
                        result.getLong("committed_publish_seq"), result.getLong("data_quality_version"),
                        nullableLong(result, "last_batch_id"), result.getLong("lock_version")));
    }

    private FileEntity file(ResultSet result, int row) throws SQLException {
        Path path = storageRoot.resolve(result.getString("object_key")).normalize();
        if (!path.startsWith(storageRoot)) {
            throw new SQLException("对象文件路径越界");
        }
        return new FileEntity(result.getLong("id"), result.getLong("tenant_id"),
                result.getString("file_name"), result.getString("content_type"),
                result.getLong("size_bytes"), result.getString("content_hash"), path,
                result.getString("file_status"), instant(result, "created_at"),
                result.getObject("workspace_id", Long.class), result.getObject("owner_member_id", Long.class));
    }

    private ImportJobEntity importJob(ResultSet result, int row) throws SQLException {
        return new ImportJobEntity(result.getLong("id"), result.getLong("tenant_id"),
                result.getLong("workspace_id"), result.getLong("dataset_id"),
                result.getLong("requester_member_id"), result.getLong("object_file_id"),
                result.getString("request_key"), read(result.getString("mapping_json"), MAPPINGS),
                result.getBoolean("strict_mode"), result.getString("import_status"),
                result.getLong("total_rows"), result.getLong("valid_rows"),
                result.getLong("error_rows"), nullableLong(result, "error_object_id"),
                result.getString("error_summary"), instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private SyncJobEntity syncJob(ResultSet result, int row) throws SQLException {
        SyncConfig config = read(result.getString("config_json"), SyncConfig.class);
        return new SyncJobEntity(result.getLong("id"), result.getLong("tenant_id"),
                result.getLong("datasource_id"), result.getLong("dataset_id"),
                result.getString("job_code"), result.getString("sync_mode"), config.sourceSchema(),
                config.sourceTable(), config.primaryKeyField(), config.updatedAtField(),
                config.amountField(), config.mappings(), config.batchSize(),
                result.getString("schedule_expression"), result.getString("timezone"),
                result.getString("job_status"), instant(result, "last_updated_at"),
                result.getString("last_primary_key"), result.getLong("lock_version"),
                instant(result, "created_at"), instant(result, "updated_at"));
    }

    private SyncRunEntity syncRun(ResultSet result, int row) throws SQLException {
        return new SyncRunEntity(result.getLong("id"), result.getLong("tenant_id"),
                result.getLong("sync_job_id"), result.getInt("attempt_no"),
                result.getString("request_key"), result.getString("run_status"),
                result.getString("lease_owner"), instant(result, "lease_until"),
                result.getLong("fencing_token"), result.getString("error_code"),
                result.getString("error_summary"), result.getInt("retry_count"),
                result.getBoolean("dead_letter"), nullableLong(result, "batch_id"),
                instant(result, "started_at"), instant(result, "finished_at"),
                instant(result, "created_at"));
    }

    private BatchEntity batch(ResultSet result, int row) throws SQLException {
        return new BatchEntity(result.getLong("id"), result.getLong("tenant_id"),
                result.getLong("dataset_id"), nullableLong(result, "sync_run_id"),
                nullableLong(result, "import_job_id"), result.getString("batch_code"),
                result.getLong("generation_id"), result.getLong("publish_seq"),
                result.getString("batch_status"), result.getLong("expected_rows"),
                result.getLong("received_rows"), result.getLong("valid_rows"),
                result.getLong("rejected_rows"), result.getLong("deduplicated_rows"),
                result.getBigDecimal("source_amount"), result.getBigDecimal("target_amount"),
                result.getLong("source_business_keys"), result.getLong("target_business_keys"),
                result.getString("quality_status"), result.getLong("fencing_token"),
                result.getString("error_report"), instant(result, "ready_at"),
                instant(result, "published_at"));
    }

    private StagedRowEntity stagedRow(ResultSet result, int row) throws SQLException {
        IdempotentEventKey key = new IdempotentEventKey(result.getLong("tenant_id"),
                result.getLong("dataset_id"), result.getLong("source_id"),
                result.getLong("generation_id"),
                result.getLong("publish_seq"), result.getString("business_key"),
                result.getString("source_version"));
        return new StagedRowEntity(key, result.getLong("batch_id"),
                result.getLong("fencing_token"), read(result.getString("values_json"), VALUE_MAP),
                result.getBigDecimal("amount"), instant(result, "source_updated_at"),
                result.getBoolean("is_deleted"));
    }

    private QualityRuleEntity qualityRule(ResultSet result, int row) throws SQLException {
        RuleConfig config = read(result.getString("rule_config"), RuleConfig.class);
        return new QualityRuleEntity(result.getLong("id"), result.getLong("tenant_id"),
                result.getLong("dataset_id"), result.getString("rule_code"),
                result.getString("rule_type"), config.field(), config.expected(),
                config.minimum(), config.maximum(), result.getString("severity"),
                result.getString("rule_status"), instant(result, "created_at"));
    }

    private MapSqlParameterSource importParameters(ImportJobEntity job) {
        return new MapSqlParameterSource().addValue("id", job.id()).addValue("tenantId", job.tenantId())
                .addValue("workspaceId", job.workspaceId()).addValue("datasetId", job.datasetId())
                .addValue("fileId", job.objectFileId()).addValue("memberId", job.requesterMemberId())
                .addValue("requestKey", job.requestKey()).addValue("mappings", json(job.mappings()))
                .addValue("strictMode", job.strictMode()).addValue("status", job.status())
                .addValue("totalRows", job.totalRows()).addValue("validRows", job.validRows())
                .addValue("errorRows", job.errorRows()).addValue("errorFileId", job.errorFileId())
                .addValue("errorSummary", job.errorSummary()).addValue("createdAt", timestamp(job.createdAt()))
                .addValue("updatedAt", timestamp(job.updatedAt()));
    }

    private MapSqlParameterSource syncJobParameters(SyncJobEntity job) {
        SyncConfig config = new SyncConfig(job.sourceSchema(), job.sourceTable(),
                job.primaryKeyField(), job.updatedAtField(), job.amountField(),
                job.mappings(), job.batchSize());
        return new MapSqlParameterSource().addValue("id", job.id()).addValue("tenantId", job.tenantId())
                .addValue("datasourceId", job.datasourceId()).addValue("datasetId", job.datasetId())
                .addValue("jobCode", job.jobCode()).addValue("mode", job.syncMode())
                .addValue("schedule", job.scheduleExpression()).addValue("timezone", job.timezone())
                .addValue("config", json(config)).addValue("status", job.status())
                .addValue("lockVersion", job.lockVersion()).addValue("createdAt", timestamp(job.createdAt()))
                .addValue("updatedAt", timestamp(job.updatedAt()));
    }

    private MapSqlParameterSource runParameters(SyncRunEntity run) {
        return new MapSqlParameterSource().addValue("id", run.id()).addValue("tenantId", run.tenantId())
                .addValue("jobId", run.syncJobId()).addValue("attempt", run.attemptNo())
                .addValue("requestKey", run.requestKey()).addValue("status", run.status())
                .addValue("leaseOwner", run.leaseOwner()).addValue("leaseUntil", timestamp(run.leaseUntil()))
                .addValue("token", run.fencingToken()).addValue("startedAt", timestamp(run.startedAt()))
                .addValue("finishedAt", timestamp(run.finishedAt())).addValue("errorCode", run.errorCode())
                .addValue("errorSummary", run.errorSummary()).addValue("retryCount", run.retryCount())
                .addValue("deadLetter", run.deadLetter()).addValue("createdAt", timestamp(run.createdAt()));
    }

    private MapSqlParameterSource batchParameters(BatchEntity batch) {
        return new MapSqlParameterSource().addValue("id", batch.id()).addValue("tenantId", batch.tenantId())
                .addValue("datasetId", batch.datasetId()).addValue("runId", batch.syncRunId())
                .addValue("importId", batch.importJobId()).addValue("code", batch.batchCode())
                .addValue("generation", batch.generationId()).addValue("publishSeq", batch.publishSeq())
                .addValue("status", batch.status()).addValue("expected", batch.expectedRows())
                .addValue("received", batch.receivedRows()).addValue("valid", batch.validRows())
                .addValue("rejected", batch.rejectedRows()).addValue("deduplicated", batch.deduplicatedRows())
                .addValue("sourceAmount", batch.sourceAmount()).addValue("targetAmount", batch.targetAmount())
                .addValue("sourceKeys", batch.sourceBusinessKeys()).addValue("targetKeys", batch.targetBusinessKeys())
                .addValue("quality", batch.qualityStatus()).addValue("token", batch.fencingToken())
                .addValue("errorReport", batch.errorReport()).addValue("readyAt", timestamp(batch.readyAt()))
                .addValue("publishedAt", timestamp(batch.publishedAt()));
    }

    private MapSqlParameterSource qualityRuleParameters(QualityRuleEntity rule) {
        RuleConfig config = new RuleConfig(rule.field(), rule.expected(), rule.minimum(), rule.maximum());
        return new MapSqlParameterSource().addValue("id", rule.id()).addValue("tenantId", rule.tenantId())
                .addValue("datasetId", rule.datasetId()).addValue("code", rule.ruleCode())
                .addValue("type", rule.ruleType()).addValue("config", json(config))
                .addValue("severity", rule.severity()).addValue("status", rule.status())
                .addValue("createdAt", timestamp(rule.createdAt()));
    }

    private MapSqlParameterSource eventParameters(StagedRowEntity row) {
        return eventKeyParameters(row.eventKey()).addValue("batchId", row.batchId())
                .addValue("token", row.fencingToken());
    }

    private MapSqlParameterSource eventKeyParameters(IdempotentEventKey key) {
        return new MapSqlParameterSource().addValue("tenantId", key.tenantId())
                .addValue("datasetId", key.datasetId()).addValue("sourceId", key.sourceId())
                .addValue("generation", key.generationId())
                .addValue("publishSeq", key.publishSeq()).addValue("businessKey", key.businessKey())
                .addValue("sourceVersion", key.sourceVersion());
    }

    private String relativeObjectKey(FileEntity file) {
        Path path = file.storagePath().toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot)) {
            throw new IllegalArgumentException("对象文件不在配置的隔离目录内");
        }
        return storageRoot.relativize(path).toString().replace('\\', '/');
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON 序列化失败", exception);
        }
    }

    private <T> T read(String value, Class<T> type) throws SQLException {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new SQLException("JSON 数据无法解析", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type) throws SQLException {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new SQLException("JSON 数据无法解析", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private long positiveVersion(String sourceVersion) {
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256")
                    .digest(sourceVersion.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
        long value = 0;
        for (int index = 0; index < Long.BYTES; index++) {
            value = (value << 8) | Byte.toUnsignedLong(hash[index]);
        }
        return value & Long.MAX_VALUE;
    }

    private long parseLongJson(String value) {
        try {
            return Long.parseLong(parseStringJson(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String parseStringJson(String value) {
        try {
            return json.readValue(value, String.class);
        } catch (JsonProcessingException exception) {
            return value;
        }
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private record SyncConfig(String sourceSchema, String sourceTable,
            String primaryKeyField, String updatedAtField, String amountField,
            List<FieldMapping> mappings, int batchSize) {
    }

    private record RuleConfig(String field, String expected, String minimum, String maximum) {
    }
}
