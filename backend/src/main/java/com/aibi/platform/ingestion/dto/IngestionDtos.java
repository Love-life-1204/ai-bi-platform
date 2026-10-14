package com.aibi.platform.ingestion.dto;

import com.aibi.platform.common.json.JsonResourceId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class IngestionDtos {

    private IngestionDtos() {
    }

    public record FileData(@JsonResourceId long id, String fileName, String contentType, long sizeBytes,
            String sha256, String status, Instant createdAt, @JsonResourceId Long workspaceId, @JsonResourceId Long ownerMemberId) {
    }

    public record ColumnPreview(String name, String inferredType, boolean nullable,
            List<String> datePatterns, List<String> samples) {
    }

    public record FilePreviewData(@JsonResourceId long fileId, String sheetName, long sampledRows,
            List<ColumnPreview> columns, List<Map<String, String>> rows, boolean truncated) {
    }

    public record FieldMapping(
            @NotBlank @Size(max = 128) String sourceField,
            @NotBlank @Size(max = 128) String targetField,
            @NotBlank @Size(max = 32) String targetType,
            @Size(max = 64) String datePattern,
            boolean required) {
    }

    public record CreateImportRequest(
            @Positive @JsonResourceId long workspaceId,
            @Positive @JsonResourceId long datasetId,
            @Positive @JsonResourceId long requesterMemberId,
            @Positive @JsonResourceId long objectFileId,
            @NotEmpty List<@Valid FieldMapping> mappings,
            boolean strictMode) {
    }

    public record ImportJobData(@JsonResourceId long id, @JsonResourceId long datasetId, @JsonResourceId long objectFileId, String status,
            long totalRows, long validRows, long errorRows, @JsonResourceId Long errorFileId,
            String errorSummary, Instant createdAt, Instant updatedAt) {
    }

    public record CreateSyncJobRequest(
            @Positive @JsonResourceId long datasourceId,
            @Positive @JsonResourceId long datasetId,
            @NotBlank @Size(max = 64) String jobCode,
            @NotBlank @Size(max = 32) String syncMode,
            @NotBlank @Size(max = 128) String sourceSchema,
            @NotBlank @Size(max = 128) String sourceTable,
            @NotBlank @Size(max = 128) String primaryKeyField,
            @Size(max = 128) String updatedAtField,
            @Size(max = 128) String amountField,
            @NotEmpty List<@Valid FieldMapping> mappings,
            @Min(100) @Max(10000) int batchSize,
            @Size(max = 128) String scheduleExpression,
            @NotBlank @Size(max = 128) String timezone,
            boolean enabled) {
    }

    public record UpdateSyncJobRequest(
            @NotBlank @Size(max = 32) String syncMode,
            @NotBlank @Size(max = 128) String sourceSchema,
            @NotBlank @Size(max = 128) String sourceTable,
            @NotBlank @Size(max = 128) String primaryKeyField,
            @Size(max = 128) String updatedAtField,
            @Size(max = 128) String amountField,
            @NotEmpty List<@Valid FieldMapping> mappings,
            @Min(100) @Max(10000) int batchSize,
            @Size(max = 128) String scheduleExpression,
            @NotBlank @Size(max = 128) String timezone,
            @Min(0) long lockVersion) {
    }

    public record SyncJobData(@JsonResourceId long id, @JsonResourceId long datasourceId, @JsonResourceId long datasetId, String jobCode,
            String syncMode, String sourceObject, String primaryKeyField,
            String updatedAtField, String amountField, List<FieldMapping> mappings,
            int batchSize, String scheduleExpression, String timezone, String status,
            Instant watermarkTime, String watermarkPrimaryKey, long lockVersion,
            Instant createdAt, Instant updatedAt) {
    }

    public record TaskAccepted(@JsonResourceId long taskId, String status) {
    }

    public record BatchData(@JsonResourceId long id, @JsonResourceId long generationId, long publishSeq, String status,
            long expectedRows, long receivedRows, long validRows, long rejectedRows,
            long deduplicatedRows, String sourceAmount, String targetAmount,
            long sourceBusinessKeys, long targetBusinessKeys, String qualityStatus,
            String errorReport, Instant publishedAt) {
    }

    public record SyncRunData(@JsonResourceId long id, @JsonResourceId long syncJobId, int attemptNo, String status,
            String leaseOwner, Instant leaseUntil, long fencingToken, String errorCode,
            String errorSummary, int retryCount, boolean deadLetter, BatchData batch,
            Instant startedAt, Instant finishedAt, Instant createdAt) {
    }

    public record QualityRuleRequest(
            @Positive @JsonResourceId long datasetId,
            @NotBlank @Size(max = 64) String ruleCode,
            @NotBlank @Size(max = 32) String ruleType,
            @NotBlank @Size(max = 128) String field,
            String expected,
            String minimum,
            String maximum,
            @NotBlank @Size(max = 16) String severity) {
    }

    public record QualityRuleData(@JsonResourceId long id, @JsonResourceId long datasetId, String ruleCode,
            String ruleType, String field, String expected, String minimum,
            String maximum, String severity, String status, Instant createdAt) {
    }

    public record QualityResultData(@JsonResourceId long ruleId, String ruleCode, String status,
            long failedRows, String observedValue, String expectedValue) {
    }

    public record QualityRunData(@JsonResourceId long batchId, String status, List<QualityResultData> results) {
    }

    public record CompensationRequest(
            @NotBlank @Size(max = 256) String reason,
            @NotBlank @Size(max = 128) String operator) {
    }

    public record SourcePage(List<Map<String, Object>> rows, boolean hasMore) {
        public SourcePage {
            rows = List.copyOf(rows);
        }
    }

    public record Reconciliation(long rowCount, long businessKeyCount, String amount) {
    }

    public record MappingResult(Map<String, Object> values, String businessKey,
            Instant updatedAt, String sourceVersion, boolean deleted) {
        public MappingResult {
            values = Map.copyOf(values);
        }
    }

    public record PublicationData(@JsonResourceId long datasetId, @JsonResourceId Long activeGeneration,
            long committedPublishSeq, long qualityVersion, @JsonResourceId Long lastBatchId, long lockVersion) {
    }

    public record QualityRuleConfig(String type, String field, String expected,
            String minimum, String maximum, String severity) {
        public QualityRuleConfig {
            type = require(type, "type");
            field = require(field, "field");
            severity = require(severity, "severity");
        }

        private static String require(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " 不能为空");
            }
            return value;
        }
    }

    public record IdempotentEventKey(@JsonResourceId long tenantId, @JsonResourceId long datasetId, @JsonResourceId long sourceId, @JsonResourceId long generationId,
            long publishSeq, String businessKey, String sourceVersion) {
        public IdempotentEventKey {
            businessKey = requireText(businessKey, "businessKey");
            sourceVersion = requireText(sourceVersion, "sourceVersion");
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " 不能为空");
            }
            return value;
        }
    }

    public record LeaseClaim(@JsonResourceId long runId, String owner, long fencingToken, Instant expiresAt) {
    }

    public record PageRequest(@Min(1) int page, @Min(1) @Max(100) int pageSize) {
    }

    public record ErrorRow(long rowNumber, Map<String, String> values,
            @NotNull List<String> errors) {
    }
}
