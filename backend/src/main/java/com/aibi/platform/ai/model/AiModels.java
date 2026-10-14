package com.aibi.platform.ai.model;

import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public final class AiModels {

    private AiModels() {
    }

    public record Conversation(
            long id, long tenantId, long workspaceId, long memberId, String title,
            String status, int contextVersion, Instant createdAt, Instant updatedAt,
            long lockVersion) {

        public Conversation withContextVersion(int version, Instant updated) {
            return new Conversation(id, tenantId, workspaceId, memberId, title, status,
                    version, createdAt, updated, lockVersion + 1);
        }
    }

    public record Message(
            long id, long tenantId, long conversationId, long sequence,
            String role, String content, Long taskId, Instant createdAt) {
    }

    public record ConversationContext(
            long tenantId, long conversationId, int version, JsonNode value, Instant createdAt) {
    }

    public record Task(
            long id, long tenantId, long conversationId, long memberId,
            String requestKey, String graphThreadKey, int contextVersion,
            String status, int attempt, long lastSequence, String errorCode,
            Instant deadlineAt, Instant createdAt, Instant startedAt, Instant finishedAt) {

        public Task withState(String nextStatus, int nextAttempt, long sequence,
                String nextErrorCode, Instant started, Instant finished) {
            return new Task(id, tenantId, conversationId, memberId, requestKey, graphThreadKey,
                    contextVersion, nextStatus, nextAttempt, sequence, nextErrorCode,
                    deadlineAt, createdAt, started, finished);
        }

        public Task withContextVersion(int version) {
            return new Task(id, tenantId, conversationId, memberId, requestKey, graphThreadKey,
                    version, status, attempt, lastSequence, errorCode,
                    deadlineAt, createdAt, startedAt, finishedAt);
        }
    }

    public record TaskEvent(
            long tenantId, long taskId, long sequence, String type,
            String traceId, JsonNode payload, Instant emittedAt) {
    }

    public record Authorization(long taskId, TaskAuthorizationSnapshot snapshot) {
    }

    public record ToolInvocation(
            long id, long tenantId, long taskId, int callSequence,
            String toolName, String argumentsHash, String status,
            JsonNode result, Instant startedAt, Instant finishedAt) {
    }
}

