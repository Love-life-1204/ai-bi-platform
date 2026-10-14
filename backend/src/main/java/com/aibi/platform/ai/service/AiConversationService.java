package com.aibi.platform.ai.service;

import com.aibi.platform.ai.config.AiProperties;
import com.aibi.platform.ai.dto.AiDtos.ConversationData;
import com.aibi.platform.ai.dto.AiDtos.CreateConversationRequest;
import com.aibi.platform.ai.dto.AiDtos.DelegatedContext;
import com.aibi.platform.ai.dto.AiDtos.ExecuteTaskRequest;
import com.aibi.platform.ai.dto.AiDtos.MessageData;
import com.aibi.platform.ai.dto.AiDtos.QuestionRequest;
import com.aibi.platform.ai.dto.AiDtos.TaskAcceptedData;
import com.aibi.platform.ai.mapper.AiStore;
import com.aibi.platform.ai.model.AiModels.Conversation;
import com.aibi.platform.ai.model.AiModels.Message;
import com.aibi.platform.ai.model.AiModels.Task;
import com.aibi.platform.common.api.TraceIdContext;
import com.aibi.platform.commercial.service.CommercialService;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.ForbiddenException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.identity.service.TaskAuthorizationService;
import com.aibi.platform.messaging.TaskDispatcher;
import com.aibi.platform.messaging.TaskKind;
import com.aibi.platform.semantic.manager.SemanticAccessManager;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class AiConversationService {

    private final AiStore store;
    private final SemanticAccessManager access;
    private final TaskAuthorizationService taskAuthorization;
    private final AiTaskCoordinator coordinator;
    private final AiProperties properties;
    private final CommercialService commercial;
    private final TaskDispatcher taskDispatcher;
    private final SecureRandom random = new SecureRandom();

    public AiConversationService(AiStore store, SemanticAccessManager access,
            TaskAuthorizationService taskAuthorization, AiTaskCoordinator coordinator,
            AiProperties properties, CommercialService commercial,
            TaskDispatcher taskDispatcher) {
        this.store = store;
        this.access = access;
        this.taskAuthorization = taskAuthorization;
        this.coordinator = coordinator;
        this.properties = properties;
        this.commercial = commercial;
        this.taskDispatcher = taskDispatcher;
    }

    public ConversationData create(String authorization, long tenantId,
            CreateConversationRequest request) {
        TokenService.TenantClaims claims = requireAiPermission(
                authorization, tenantId, request.workspaceId());
        Instant now = Instant.now();
        Conversation conversation = store.insertConversation(new Conversation(
                positiveId(), tenantId, request.workspaceId(), claims.memberId(),
                request.title().trim(), "ACTIVE", 0, now, now, 0));
        return data(conversation);
    }

    public List<ConversationData> list(String authorization, long tenantId) {
        TokenService.TenantClaims claims = access.requireTenant(authorization, tenantId);
        return store.listConversations(tenantId, claims.memberId()).stream().map(this::data).toList();
    }

    public ConversationData get(String authorization, long tenantId, long conversationId) {
        var claims = access.requireTenant(authorization, tenantId);
        Conversation conversation = requiredConversation(tenantId, conversationId, claims.memberId());
        requireAiPermission(authorization, tenantId, conversation.workspaceId());
        return data(conversation);
    }

    public List<MessageData> messages(String authorization, long tenantId, long conversationId) {
        var claims = access.requireTenant(authorization, tenantId);
        Conversation conversation = requiredConversation(tenantId, conversationId, claims.memberId());
        requireAiPermission(authorization, tenantId, conversation.workspaceId());
        return store.listMessages(tenantId, conversationId).stream().map(this::data).toList();
    }

    public TaskAcceptedData ask(String authorization, long tenantId, long conversationId,
            String requestKey, QuestionRequest request) {
        var claims = access.requireTenant(authorization, tenantId);
        Conversation conversation = requiredConversation(tenantId, conversationId, claims.memberId());
        TokenService.TenantClaims authorized = requireAiPermission(
                authorization, tenantId, conversation.workspaceId());
        int suppliedVersion = request.confirmedContextVersion() == null
                ? conversation.contextVersion() : request.confirmedContextVersion();
        if (suppliedVersion != conversation.contextVersion()) {
            throw new ConflictException("AI 会话上下文版本已变化，请刷新后重试");
        }
        Task existing = store.findTaskByRequest(tenantId, claims.memberId(), requestKey).orElse(null);
        if (existing != null) {
            return accepted(existing);
        }
        var snapshot = taskAuthorization.capture(authorization, tenantId,
                new AccessCheckRequest(conversation.workspaceId(), "ai:ask", null, "ask"));
        Instant now = Instant.now();
        long taskId = positiveId();
        String quotaReservation = commercial.reserveAiRequest(tenantId, taskId);
        Task draft = new Task(taskId, tenantId, conversationId,
                authorized.memberId(), requestKey, UUID.randomUUID().toString().replace("-", ""),
                conversation.contextVersion(), "ACCEPTED", 1, 0, null,
                now.plusSeconds(properties.getTaskTimeoutSeconds()), now, null, null);
        AtomicReference<Task> saved = new AtomicReference<>();
        try {
            taskDispatcher.dispatch(TaskKind.AI, tenantId, taskId, request.datasetId(), () -> {
                Task task = store.insertTask(draft, snapshot);
                long sequence = store.listMessages(tenantId, conversationId).size() + 1L;
                store.appendMessage(new Message(positiveId(), tenantId, conversationId, sequence,
                        "USER", request.question().trim(), taskId, now));
                saved.set(task);
            }, () -> start(saved.get(), conversation, snapshot,
                    request.question().trim(), request.datasetId(), suppliedVersion));
        } catch (RuntimeException exception) {
            if (quotaReservation != null) {
                commercial.settleAiRequest(tenantId, taskId, false);
            }
            throw exception;
        }
        Task task = saved.get();
        return accepted(task);
    }

    public void consumeTask(long tenantId, long taskId, Long datasetId) {
        Task task = store.findTask(tenantId, taskId).orElse(null);
        if (task == null || !List.of("ACCEPTED", "RUNNING").contains(task.status())) {
            return;
        }
        var snapshot = store.authorization(tenantId, taskId).orElseThrow(
                () -> new NotFoundException("AI 任务授权快照不存在"));
        Conversation conversation = requiredConversation(
                tenantId, task.conversationId(), task.memberId());
        Message question = store.listMessages(tenantId, task.conversationId()).stream()
                .filter(message -> message.taskId() != null && message.taskId() == taskId)
                .filter(message -> "USER".equals(message.role()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new NotFoundException("AI 任务问题不存在"));
        start(task, conversation, snapshot, question.content(), datasetId, task.contextVersion());
    }

    private void start(Task task, Conversation conversation,
            com.aibi.platform.identity.dto.TaskAuthorizationSnapshot snapshot,
            String question, Long datasetId, int contextVersion) {
        DelegatedContext context = delegated(task, conversation, snapshot,
                UUID.randomUUID().toString().replace("-", ""));
        coordinator.start(task, new ExecuteTaskRequest(
                context, question, datasetId, contextVersion));
    }

    Conversation requiredConversation(long tenantId, long conversationId, long memberId) {
        Conversation conversation = store.findConversation(tenantId, conversationId)
                .orElseThrow(() -> new NotFoundException("AI 会话不存在"));
        if (conversation.memberId() != memberId || !"ACTIVE".equals(conversation.status())) {
            throw new ForbiddenException();
        }
        return conversation;
    }

    DelegatedContext delegated(Task task, Conversation conversation,
            com.aibi.platform.identity.dto.TaskAuthorizationSnapshot snapshot, String requestId) {
        Instant expiresAt = task.deadlineAt().plusSeconds(15);
        String traceId = TraceIdContext.currentTraceId();
        if (traceId == null || traceId.length() < 8) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        return new DelegatedContext("ai-bi-java", "ai-bi-engine",
                UUID.randomUUID().toString().replace("-", ""), expiresAt, task.deadlineAt(),
                task.tenantId(), task.memberId(), conversation.workspaceId(), snapshot.userId(),
                snapshot.sessionId(), snapshot.tenantPermissionVersion(),
                snapshot.memberPermissionVersion(), task.contextVersion(), requestId,
                String.valueOf(task.id()), String.valueOf(task.conversationId()),
                task.graphThreadKey(), traceId,
                List.of("catalog_search", "query_validate", "query_execute"));
    }

    private TokenService.TenantClaims requireAiPermission(
            String authorization, long tenantId, long workspaceId) {
        TokenService.TenantClaims claims = access.requireWorkspace(authorization, tenantId, workspaceId);
        access.requirePermission(authorization, tenantId, workspaceId, "ai:ask", "ask");
        return claims;
    }

    private TaskAcceptedData accepted(Task task) {
        return new TaskAcceptedData("req-" + task.id(), String.valueOf(task.id()), task.status(),
                "/api/ai/tasks/" + task.id() + "/events");
    }

    private ConversationData data(Conversation value) {
        return new ConversationData(value.id(), value.workspaceId(), value.memberId(),
                value.title(), value.status(), value.contextVersion(),
                value.createdAt(), value.updatedAt());
    }

    private MessageData data(Message value) {
        return new MessageData(value.id(), value.conversationId(), value.sequence(), value.role(),
                value.content(), value.taskId(), value.createdAt());
    }

    private long positiveId() {
        return random.nextLong(1, Long.MAX_VALUE);
    }
}

