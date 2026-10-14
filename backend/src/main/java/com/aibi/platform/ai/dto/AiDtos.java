package com.aibi.platform.ai.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.common.json.JsonResourceIds;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AiDtos {

    private AiDtos() {
    }

    public record CreateConversationRequest(
            @Positive @JsonResourceId long workspaceId,
            @NotBlank @Size(max = 200) String title) {
    }

    public record ConversationData(
            @JsonResourceId long id, @JsonResourceId long workspaceId, @JsonResourceId long memberId, String title,
            String status, int contextVersion, Instant createdAt, Instant updatedAt) {
    }

    public record MessageData(
            @JsonResourceId long id, @JsonResourceId long conversationId, long sequence, String role,
            String content, @JsonResourceId Long taskId, Instant createdAt) {
    }

    public record QuestionRequest(
            @NotBlank @Size(max = 4000) String question,
            @Positive @JsonResourceId Long datasetId,
            @Min(0) Integer confirmedContextVersion) {
    }

    public record ResumeRequest(
            @NotNull Map<@NotBlank String, Object> clarification,
            @Min(0) int confirmedContextVersion) {
    }

    public record FeedbackRequest(
            @Min(0) @Max(1) int rating,
            @Size(max = 2000) String comment) {
    }

    public record TaskAcceptedData(
            @JsonAlias("request_id") String requestId,
            @JsonAlias("task_id") String taskId,
            String status,
            String eventsUrl) {
    }

    public record TaskData(
            String taskId, @JsonResourceId long conversationId, String status, int attempt,
            int contextVersion, long lastSequence, String errorCode,
            Instant deadlineAt, Instant createdAt, Instant finishedAt) {
    }

    public record TaskEventData(
            @JsonAlias("event_id") String eventId,
            @JsonAlias("sequence") long sequence,
            @JsonAlias("task_id") String taskId,
            @JsonAlias("trace_id") String traceId,
            @JsonAlias("type") String type,
            @JsonAlias("emitted_at") Instant emittedAt,
            JsonNode payload) {
    }

    public record TaskStatusResponse(
            @JsonAlias("task_id") String taskId,
            String status,
            @JsonAlias("last_sequence") long lastSequence,
            @JsonAlias("error_code") String errorCode,
            JsonNode clarification,
            JsonNode result) {
    }

    public record DelegatedContext(
            String issuer,
            String audience,
            String jti,
            @JsonProperty("expires_at") Instant expiresAt,
            @JsonProperty("deadline_at") Instant deadlineAt,
            @JsonProperty("tenant_id") @JsonResourceId long tenantId,
            @JsonProperty("member_id") @JsonResourceId long memberId,
            @JsonProperty("workspace_id") @JsonResourceId long workspaceId,
            @JsonProperty("user_id") @JsonResourceId long userId,
            @JsonProperty("session_id") @JsonResourceId long sessionId,
            @JsonProperty("tenant_permission_version") long tenantPermissionVersion,
            @JsonProperty("member_permission_version") long memberPermissionVersion,
            @JsonProperty("context_version") int contextVersion,
            @JsonProperty("request_id") String requestId,
            @JsonProperty("task_id") String taskId,
            @JsonProperty("conversation_id") String conversationId,
            @JsonProperty("thread_id") String threadId,
            @JsonProperty("trace_id") String traceId,
            @JsonProperty("allowed_tools") List<String> allowedTools) {
    }

    public record ExecuteTaskRequest(
            DelegatedContext context,
            String question,
            @JsonProperty("dataset_id") @JsonResourceId Long datasetId,
            @JsonProperty("confirmed_context_version") Integer confirmedContextVersion) {
    }

    public record ResumeTaskRequest(DelegatedContext context, Map<String, Object> clarification) {
    }

    public record SemanticSearchRequest(
            @JsonAlias("task_id") String taskId,
            String question,
            @JsonAlias("dataset_id") @JsonResourceId Long datasetId,
            @JsonAlias("tool_call_id") String toolCallId) {
    }

    public record InternalQueryRequest(
            @JsonAlias("task_id") String taskId,
            @NotNull QueryPlan plan,
            @JsonAlias("tool_call_id") @NotBlank String toolCallId) {
    }

    public record SemanticFieldData(
            @JsonProperty("field_id") @JsonResourceId long fieldId,
            String key,
            String name,
            @JsonProperty("data_type") String dataType,
            String role) {
    }

    public record SemanticDatasetData(
            @JsonProperty("dataset_id") @JsonResourceId long datasetId,
            @JsonProperty("dataset_version_id") @JsonResourceId long datasetVersionId,
            String code, String name, List<SemanticFieldData> fields) {
    }

    public record SemanticMetricData(
            @JsonProperty("metric_version_id") @JsonResourceId long metricVersionId,
            @JsonProperty("metric_id") @JsonResourceId long metricId,
            @JsonProperty("dataset_version_id") @JsonResourceId long datasetVersionId,
            String code, String name, String unit, List<String> aliases,
            @JsonProperty("allowed_dimension_field_ids") @JsonResourceIds List<Long> allowedDimensionFieldIds,
            @JsonProperty("time_field_id") @JsonResourceId Long timeFieldId) {
    }

    public record SemanticCatalogData(
            List<SemanticDatasetData> datasets,
            List<SemanticMetricData> metrics,
            List<Map<String, Object>> terms,
            @JsonProperty("semantic_version") String semanticVersion) {
    }
}

