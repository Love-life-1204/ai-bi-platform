package com.aibi.platform.ai.controller;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.ai.dto.AiDtos.ConversationData;
import com.aibi.platform.ai.dto.AiDtos.CreateConversationRequest;
import com.aibi.platform.ai.dto.AiDtos.FeedbackRequest;
import com.aibi.platform.ai.dto.AiDtos.MessageData;
import com.aibi.platform.ai.dto.AiDtos.QuestionRequest;
import com.aibi.platform.ai.dto.AiDtos.ResumeRequest;
import com.aibi.platform.ai.dto.AiDtos.TaskAcceptedData;
import com.aibi.platform.ai.dto.AiDtos.TaskData;
import com.aibi.platform.ai.service.AiConversationService;
import com.aibi.platform.ai.service.AiTaskService;
import com.aibi.platform.common.idempotency.Idempotent;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping({"/api/ai", "/api/v1/ai"})
@Tag(name = "AI 智能分析", description = "会话、任务、澄清恢复、SSE、答案和证据")
public class AiController {

    private final AiConversationService conversations;
    private final AiTaskService tasks;

    public AiController(AiConversationService conversations, AiTaskService tasks) {
        this.conversations = conversations;
        this.tasks = tasks;
    }

    @PostMapping("/conversations")
    @Operation(summary = "创建工作空间内 AI 会话")
    public ConversationData createConversation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @Valid @RequestBody CreateConversationRequest request) {
        return conversations.create(authorization, tenantId, request);
    }

    @GetMapping("/conversations")
    public List<ConversationData> conversations(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId) {
        return conversations.list(authorization, tenantId);
    }

    @GetMapping("/conversations/{conversationId}")
    public ConversationData conversation(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long conversationId) {
        return conversations.get(authorization, tenantId, conversationId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<MessageData> messages(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long conversationId) {
        return conversations.messages(authorization, tenantId, conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Idempotent
    public TaskAcceptedData ask(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestHeader("Idempotency-Key") String requestKey,
            @PathVariable @Positive @JsonResourceId long conversationId,
            @Valid @RequestBody QuestionRequest request) {
        return conversations.ask(authorization, tenantId, conversationId, requestKey, request);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskData task(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long taskId) {
        return tasks.status(authorization, tenantId, taskId);
    }

    @GetMapping(value = "/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @PathVariable @Positive @JsonResourceId long taskId) {
        return tasks.events(authorization, tenantId, taskId, lastEventId);
    }

    @PostMapping("/tasks/{taskId}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Idempotent
    public TaskAcceptedData resume(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long taskId,
            @Valid @RequestBody ResumeRequest request) {
        return tasks.resume(authorization, tenantId, taskId, request);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public TaskData cancel(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long taskId) {
        return tasks.cancel(authorization, tenantId, taskId);
    }

    @PostMapping("/tasks/{taskId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void feedback(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long taskId,
            @Valid @RequestBody FeedbackRequest request) {
        tasks.feedback(authorization, tenantId, taskId, request);
    }

    @GetMapping("/tasks/{taskId}/answer")
    public JsonNode answer(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long taskId) {
        return tasks.answer(authorization, tenantId, taskId);
    }

    @GetMapping("/tasks/{taskId}/evidence")
    public JsonNode evidence(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Tenant-Id") @Positive @JsonResourceId long tenantId,
            @PathVariable @Positive @JsonResourceId long taskId) {
        return tasks.evidence(authorization, tenantId, taskId);
    }
}

