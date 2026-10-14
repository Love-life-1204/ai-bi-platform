package com.aibi.platform.ai.mapper;

import com.aibi.platform.ai.model.AiModels.Conversation;
import com.aibi.platform.ai.model.AiModels.ConversationContext;
import com.aibi.platform.ai.model.AiModels.Message;
import com.aibi.platform.ai.model.AiModels.Task;
import com.aibi.platform.ai.model.AiModels.TaskEvent;
import com.aibi.platform.ai.model.AiModels.ToolInvocation;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryAiStore implements AiStore {

    private final Map<Long, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, ConversationContext> contexts = new ConcurrentHashMap<>();
    private final Map<Long, Message> messages = new ConcurrentHashMap<>();
    private final Map<Long, Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, TaskAuthorizationSnapshot> authorizations = new ConcurrentHashMap<>();
    private final Map<String, TaskEvent> taskEvents = new ConcurrentHashMap<>();
    private final Map<String, ToolInvocation> toolInvocations = new ConcurrentHashMap<>();
    private final Map<String, String> requestKeys = new ConcurrentHashMap<>();
    private final Map<String, String> feedback = new ConcurrentHashMap<>();

    @Override
    public Conversation insertConversation(Conversation conversation) {
        if (conversations.putIfAbsent(conversation.id(), conversation) != null) {
            throw new ConflictException("AI 会话已存在");
        }
        return conversation;
    }

    @Override
    public Optional<Conversation> findConversation(long tenantId, long conversationId) {
        return Optional.ofNullable(conversations.get(conversationId))
                .filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public List<Conversation> listConversations(long tenantId, long memberId) {
        return conversations.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.memberId() == memberId)
                .sorted(Comparator.comparing(Conversation::updatedAt).reversed()).toList();
    }

    @Override
    public synchronized Conversation saveContext(Conversation conversation,
            ConversationContext context) {
        Conversation current = findConversation(conversation.tenantId(), conversation.id())
                .orElseThrow(() -> new NotFoundException("AI 会话不存在"));
        if (context.version() != current.contextVersion() + 1) {
            throw new ConflictException("AI 会话上下文版本冲突");
        }
        contexts.put(contextKey(context.tenantId(), context.conversationId(), context.version()), context);
        conversations.put(current.id(), conversation);
        return conversation;
    }

    @Override
    public Optional<ConversationContext> latestContext(long tenantId, long conversationId) {
        return contexts.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.conversationId() == conversationId)
                .max(Comparator.comparingInt(ConversationContext::version));
    }

    @Override
    public synchronized Message appendMessage(Message message) {
        long expected = messages.values().stream()
                .filter(value -> value.tenantId() == message.tenantId()
                        && value.conversationId() == message.conversationId())
                .mapToLong(Message::sequence).max().orElse(0) + 1;
        if (message.sequence() != expected || messages.putIfAbsent(message.id(), message) != null) {
            throw new ConflictException("AI 消息序号冲突");
        }
        return message;
    }

    @Override
    public List<Message> listMessages(long tenantId, long conversationId) {
        return messages.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.conversationId() == conversationId)
                .sorted(Comparator.comparingLong(Message::sequence)).toList();
    }

    @Override
    public synchronized Task insertTask(Task task, TaskAuthorizationSnapshot snapshot) {
        String requestKey = requestKey(task.tenantId(), task.memberId(), task.requestKey());
        if (requestKeys.putIfAbsent(requestKey, String.valueOf(task.id())) != null
                || tasks.putIfAbsent(task.id(), task) != null) {
            throw new ConflictException("AI 任务幂等键已存在");
        }
        authorizations.put(taskKey(task.tenantId(), task.id()), snapshot);
        return task;
    }

    @Override
    public Optional<Task> findTask(long tenantId, long taskId) {
        return Optional.ofNullable(tasks.get(taskId)).filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public Optional<Task> findTaskByGlobalId(long taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public Optional<Task> findTaskByRequest(long tenantId, long memberId, String requestKey) {
        String id = requestKeys.get(requestKey(tenantId, memberId, requestKey));
        return id == null ? Optional.empty() : findTask(tenantId, Long.parseLong(id));
    }

    @Override
    public Task updateTask(Task task) {
        tasks.compute(task.id(), (id, current) -> {
            if (current == null || current.tenantId() != task.tenantId()) {
                throw new NotFoundException("AI 任务不存在");
            }
            return task;
        });
        return task;
    }

    @Override
    public synchronized TaskEvent appendEvent(TaskEvent event) {
        long expected = taskEvents.values().stream()
                .filter(value -> value.tenantId() == event.tenantId() && value.taskId() == event.taskId())
                .mapToLong(TaskEvent::sequence).max().orElse(0) + 1;
        if (event.sequence() != expected) {
            throw new ConflictException("AI 事件序号不连续");
        }
        taskEvents.put(eventKey(event.tenantId(), event.taskId(), event.sequence()), event);
        return event;
    }

    @Override
    public List<TaskEvent> events(long tenantId, long taskId, long afterSequence, int limit) {
        return taskEvents.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.taskId() == taskId
                        && value.sequence() > afterSequence)
                .sorted(Comparator.comparingLong(TaskEvent::sequence)).limit(limit).toList();
    }

    @Override
    public Optional<TaskEvent> latestEvent(long tenantId, long taskId, String type) {
        return taskEvents.values().stream()
                .filter(value -> value.tenantId() == tenantId && value.taskId() == taskId
                        && type.equals(value.type()))
                .max(Comparator.comparingLong(TaskEvent::sequence));
    }

    @Override
    public Optional<TaskAuthorizationSnapshot> authorization(long tenantId, long taskId) {
        return Optional.ofNullable(authorizations.get(taskKey(tenantId, taskId)));
    }

    @Override
    public Optional<ToolInvocation> toolInvocation(long tenantId, long taskId, int callSequence) {
        return Optional.ofNullable(toolInvocations.get(toolKey(tenantId, taskId, callSequence)));
    }

    @Override
    public boolean claimToolInvocation(ToolInvocation invocation) {
        String key = toolKey(invocation.tenantId(), invocation.taskId(), invocation.callSequence());
        return toolInvocations.putIfAbsent(key, invocation) == null;
    }

    @Override
    public void completeToolInvocation(long tenantId, long taskId, int callSequence,
            JsonNode result, Instant finishedAt) {
        toolInvocations.compute(toolKey(tenantId, taskId, callSequence), (key, current) -> {
            if (current == null) {
                throw new NotFoundException("AI 工具调用不存在");
            }
            return new ToolInvocation(current.id(), tenantId, taskId, callSequence,
                    current.toolName(), current.argumentsHash(), "SUCCEEDED", result,
                    current.startedAt(), finishedAt);
        });
    }

    @Override
    public void saveFeedback(long tenantId, long taskId, long memberId, int rating, String comment) {
        feedback.put(taskKey(tenantId, taskId) + ":" + memberId, rating + ":" + comment);
    }

    private String contextKey(long tenantId, long conversationId, int version) {
        return tenantId + ":" + conversationId + ":" + version;
    }

    private String taskKey(long tenantId, long taskId) {
        return tenantId + ":" + taskId;
    }

    private String eventKey(long tenantId, long taskId, long sequence) {
        return taskKey(tenantId, taskId) + ":" + sequence;
    }

    private String toolKey(long tenantId, long taskId, int callSequence) {
        return taskKey(tenantId, taskId) + ":tool:" + callSequence;
    }

    private String requestKey(long tenantId, long memberId, String value) {
        return tenantId + ":" + memberId + ":" + value;
    }
}

