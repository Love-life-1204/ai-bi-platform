package com.aibi.platform.ai.service;

import com.aibi.platform.ai.client.AiServiceClient;
import com.aibi.platform.ai.dto.AiDtos.ExecuteTaskRequest;
import com.aibi.platform.ai.dto.AiDtos.ResumeTaskRequest;
import com.aibi.platform.ai.dto.AiDtos.TaskEventData;
import com.aibi.platform.ai.mapper.AiStore;
import com.aibi.platform.ai.manager.AiEventBroker;
import com.aibi.platform.ai.model.AiModels.Message;
import com.aibi.platform.ai.model.AiModels.Task;
import com.aibi.platform.ai.model.AiModels.TaskEvent;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.commercial.service.CommercialService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class AiTaskCoordinator {

    private static final List<String> TERMINAL = List.of(
            "SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "CANCELLED", "TIMEOUT");
    private final AiStore store;
    private final AiServiceClient client;
    private final AiEventBroker broker;
    private final TaskExecutor executor;
    private final SecureRandom random = new SecureRandom();
    private final CommercialService commercial;

    public AiTaskCoordinator(AiStore store, AiServiceClient client, AiEventBroker broker,
            @Qualifier("aiTaskExecutor") TaskExecutor executor, CommercialService commercial) {
        this.store = store;
        this.client = client;
        this.broker = broker;
        this.executor = executor;
        this.commercial = commercial;
    }

    public void start(Task task, ExecuteTaskRequest request) {
        Task running = task.withState("RUNNING", task.attempt(), task.lastSequence(), null,
                Instant.now(), null);
        store.updateTask(running);
        try {
            client.execute(request);
        } catch (ApiException ignored) {
            // The request may have reached the worker before a network failure. Polling is
            // deliberately started so the idempotent task can still converge.
        }
        executor.execute(() -> poll(running));
    }

    public void resume(Task task, ResumeTaskRequest request) {
        store.updateTask(task);
        try {
            client.resume(String.valueOf(task.id()), request);
        } catch (ApiException ignored) {
            // Resume is idempotent. Poll until the deadline in case the response was lost.
        }
        executor.execute(() -> poll(task));
    }

    public void cancel(Task task) {
        client.cancel(String.valueOf(task.id()));
        store.updateTask(task.withState("CANCEL_REQUESTED", task.attempt(), task.lastSequence(),
                task.errorCode(), task.startedAt(), null));
    }

    private void poll(Task initial) {
        Task task = initial;
        while (!TERMINAL.contains(task.status()) && Instant.now().isBefore(task.deadlineAt())) {
            List<TaskEventData> incoming;
            try {
                incoming = client.events(String.valueOf(task.id()), task.lastSequence());
            } catch (ApiException exception) {
                if (!pause(500)) {
                    return;
                }
                continue;
            }
            if (incoming == null) {
                incoming = List.of();
            }
            for (TaskEventData event : incoming) {
                TaskEvent stored = new TaskEvent(task.tenantId(), task.id(), event.sequence(),
                        event.type(), event.traceId(), event.payload(), event.emittedAt());
                store.appendEvent(stored);
                task = apply(task, event);
                store.updateTask(task);
                broker.publish(stored);
                if ("result".equals(event.type())) {
                    appendAssistantMessage(task, event.payload());
                }
            }
            if (TERMINAL.contains(task.status())) {
                commercial.settleAiRequest(task.tenantId(), task.id(),
                        "SUCCEEDED".equals(task.status()) || "PARTIAL_SUCCESS".equals(task.status()));
                return;
            }
            if (!pause(250)) {
                return;
            }
        }
        if (!TERMINAL.contains(task.status())) {
            timeout(task);
        }
    }

    private void timeout(Task task) {
        try {
            client.cancel(String.valueOf(task.id()));
        } catch (ApiException ignored) {
            // The Java-side deadline remains authoritative when the worker is unavailable.
        }
        Instant now = Instant.now();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        var errorPayload = JsonNodeFactory.instance.objectNode()
                .put("code", "AI_TASK_TIMEOUT").put("message", "智能分析任务已超时");
        TaskEvent error = new TaskEvent(task.tenantId(), task.id(), task.lastSequence() + 1,
                "error", traceId, errorPayload, now);
        store.appendEvent(error);
        broker.publish(error);
        var donePayload = JsonNodeFactory.instance.objectNode().put("status", "TIMEOUT");
        TaskEvent done = new TaskEvent(task.tenantId(), task.id(), task.lastSequence() + 2,
                "done", traceId, donePayload, now);
        store.appendEvent(done);
        store.updateTask(task.withState("TIMEOUT", task.attempt(), done.sequence(),
                "AI_TASK_TIMEOUT", task.startedAt(), now));
        commercial.settleAiRequest(task.tenantId(), task.id(), false);
        broker.publish(done);
    }

    private boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Task apply(Task task, TaskEventData event) {
        String status = task.status();
        String error = task.errorCode();
        Instant finished = task.finishedAt();
        if ("clarification".equals(event.type())) {
            status = "WAITING_CLARIFICATION";
        } else if ("error".equals(event.type())) {
            error = event.payload().path("code").asText("AI_GRAPH_FAILED");
        } else if ("done".equals(event.type())) {
            status = event.payload().path("status").asText("FAILED");
            finished = Instant.now();
        }
        return task.withState(status, task.attempt(), event.sequence(), error,
                task.startedAt(), finished);
    }

    private void appendAssistantMessage(Task task, JsonNode payload) {
        String summary = payload.path("answer").path("summary").asText("");
        if (summary.isBlank()) {
            return;
        }
        long sequence = store.listMessages(task.tenantId(), task.conversationId()).size() + 1L;
        store.appendMessage(new Message(random.nextLong(1, Long.MAX_VALUE), task.tenantId(),
                task.conversationId(), sequence, "ASSISTANT", summary, task.id(), Instant.now()));
    }
}

