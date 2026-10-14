package com.aibi.platform.ai.service;

import com.aibi.platform.ai.dto.AiDtos.DelegatedContext;
import com.aibi.platform.ai.dto.AiDtos.FeedbackRequest;
import com.aibi.platform.ai.dto.AiDtos.ResumeRequest;
import com.aibi.platform.ai.dto.AiDtos.ResumeTaskRequest;
import com.aibi.platform.ai.dto.AiDtos.TaskAcceptedData;
import com.aibi.platform.ai.dto.AiDtos.TaskData;
import com.aibi.platform.ai.mapper.AiStore;
import com.aibi.platform.ai.manager.AiEventBroker;
import com.aibi.platform.ai.manager.DelegatedQueryManager;
import com.aibi.platform.ai.model.AiModels.ConversationContext;
import com.aibi.platform.ai.model.AiModels.Task;
import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.service.PermissionContextService;
import com.aibi.platform.identity.service.TaskAuthorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiTaskService {

    private final AiStore store;
    private final PermissionContextService permissionContext;
    private final TaskAuthorizationService taskAuthorization;
    private final AiConversationService conversations;
    private final AiTaskCoordinator coordinator;
    private final AiEventBroker broker;
    private final DelegatedQueryManager queries;
    private final ObjectMapper objectMapper;
    private final com.aibi.platform.ai.config.AiProperties properties;

    public AiTaskService(AiStore store, PermissionContextService permissionContext,
            TaskAuthorizationService taskAuthorization, AiConversationService conversations,
            AiTaskCoordinator coordinator, AiEventBroker broker, DelegatedQueryManager queries,
            ObjectMapper objectMapper, com.aibi.platform.ai.config.AiProperties properties) {
        this.store = store;
        this.permissionContext = permissionContext;
        this.taskAuthorization = taskAuthorization;
        this.conversations = conversations;
        this.coordinator = coordinator;
        this.broker = broker;
        this.queries = queries;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public TaskData status(String authorization, long tenantId, long taskId) {
        return data(requiredTask(authorization, tenantId, taskId));
    }

    public SseEmitter events(String authorization, long tenantId, long taskId, String lastEventId) {
        Task task = requiredTask(authorization, tenantId, taskId);
        long after = parseSequence(taskId, lastEventId);
        return broker.subscribe(taskId, store.events(tenantId, taskId, after, 500));
    }

    public TaskAcceptedData resume(String authorization, long tenantId, long taskId,
            ResumeRequest request) {
        Task current = requiredTask(authorization, tenantId, taskId);
        if (!"WAITING_CLARIFICATION".equals(current.status())) {
            throw new ConflictException("AI 任务当前不处于待澄清状态");
        }
        var snapshot = store.authorization(tenantId, taskId)
                .orElseThrow(() -> new NotFoundException("AI 任务授权快照不存在"));
        taskAuthorization.reauthorize(snapshot);
        var conversation = conversations.requiredConversation(
                tenantId, current.conversationId(), current.memberId());
        if (request.confirmedContextVersion() != conversation.contextVersion()) {
            throw new ConflictException("AI 会话上下文版本已变化，请刷新后重试");
        }
        int nextVersion = conversation.contextVersion() + 1;
        Instant now = Instant.now();
        JsonNode contextValue = objectMapper.valueToTree(request.clarification());
        var updatedConversation = conversation.withContextVersion(nextVersion, now);
        store.saveContext(updatedConversation, new ConversationContext(
                tenantId, conversation.id(), nextVersion, contextValue, now));
        Task resumed = new Task(current.id(), current.tenantId(), current.conversationId(),
                current.memberId(), current.requestKey(), current.graphThreadKey(), nextVersion,
                "RUNNING", current.attempt() + 1, current.lastSequence(), null,
                now.plusSeconds(properties.getTaskTimeoutSeconds()), current.createdAt(), now, null);
        store.updateTask(resumed);
        DelegatedContext delegated = conversations.delegated(resumed, updatedConversation,
                snapshot, UUID.randomUUID().toString().replace("-", ""));
        coordinator.resume(resumed, new ResumeTaskRequest(delegated, request.clarification()));
        return new TaskAcceptedData(delegated.requestId(), String.valueOf(taskId), "ACCEPTED",
                "/api/ai/tasks/" + taskId + "/events");
    }

    public TaskData cancel(String authorization, long tenantId, long taskId) {
        Task task = requiredTask(authorization, tenantId, taskId);
        if (java.util.List.of("SUCCEEDED", "FAILED", "CANCELLED", "TIMEOUT").contains(task.status())) {
            return data(task);
        }
        queries.cancel(taskId);
        coordinator.cancel(task);
        return data(store.findTask(tenantId, taskId).orElse(task));
    }

    public void feedback(String authorization, long tenantId, long taskId, FeedbackRequest request) {
        Task task = requiredTask(authorization, tenantId, taskId);
        store.saveFeedback(tenantId, taskId, task.memberId(), request.rating(), request.comment());
    }

    public JsonNode answer(String authorization, long tenantId, long taskId) {
        requiredSucceeded(authorization, tenantId, taskId);
        JsonNode result = store.latestResult(tenantId, taskId);
        if (result == null) {
            throw new NotFoundException("AI 答案已过期或尚未生成");
        }
        return result.path("answer");
    }

    public JsonNode evidence(String authorization, long tenantId, long taskId) {
        requiredSucceeded(authorization, tenantId, taskId);
        JsonNode result = store.latestResult(tenantId, taskId);
        if (result == null) {
            throw new NotFoundException("AI 证据已过期或尚未生成");
        }
        return result.path("evidence");
    }

    private Task requiredSucceeded(String authorization, long tenantId, long taskId) {
        Task task = requiredTask(authorization, tenantId, taskId);
        if (!java.util.List.of("SUCCEEDED", "PARTIAL_SUCCESS").contains(task.status())) {
            throw new ConflictException("AI 任务尚未成功完成");
        }
        return task;
    }

    private Task requiredTask(String authorization, long tenantId, long taskId) {
        var claims = permissionContext.requireContext(authorization, tenantId);
        Task task = store.findTask(tenantId, taskId)
                .orElseThrow(() -> new NotFoundException("AI 任务不存在"));
        if (task.memberId() != claims.memberId()) {
            throw new ForbiddenException();
        }
        var snapshot = store.authorization(tenantId, taskId)
                .orElseThrow(() -> new NotFoundException("AI 任务授权快照不存在"));
        taskAuthorization.reauthorize(snapshot);
        return task;
    }

    private long parseSequence(long taskId, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return 0;
        }
        String prefix = taskId + ":";
        if (!eventId.startsWith(prefix)) {
            throw new BadRequestException("Last-Event-ID 不属于当前任务");
        }
        try {
            return Math.max(0, Long.parseLong(eventId.substring(prefix.length())));
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Last-Event-ID 格式错误");
        }
    }

    private TaskData data(Task value) {
        return new TaskData(String.valueOf(value.id()), value.conversationId(), value.status(),
                value.attempt(), value.contextVersion(), value.lastSequence(), value.errorCode(),
                value.deadlineAt(), value.createdAt(), value.finishedAt());
    }
}

