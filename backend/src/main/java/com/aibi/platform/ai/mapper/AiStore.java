package com.aibi.platform.ai.mapper;

import com.aibi.platform.ai.model.AiModels.Conversation;
import com.aibi.platform.ai.model.AiModels.ConversationContext;
import com.aibi.platform.ai.model.AiModels.Message;
import com.aibi.platform.ai.model.AiModels.Task;
import com.aibi.platform.ai.model.AiModels.TaskEvent;
import com.aibi.platform.ai.model.AiModels.ToolInvocation;
import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;

public interface AiStore {

    Conversation insertConversation(Conversation conversation);

    Optional<Conversation> findConversation(long tenantId, long conversationId);

    List<Conversation> listConversations(long tenantId, long memberId);

    Conversation saveContext(Conversation conversation, ConversationContext context);

    Optional<ConversationContext> latestContext(long tenantId, long conversationId);

    Message appendMessage(Message message);

    List<Message> listMessages(long tenantId, long conversationId);

    Task insertTask(Task task, TaskAuthorizationSnapshot snapshot);

    Optional<Task> findTask(long tenantId, long taskId);

    Optional<Task> findTaskByGlobalId(long taskId);

    Optional<Task> findTaskByRequest(long tenantId, long memberId, String requestKey);

    Task updateTask(Task task);

    TaskEvent appendEvent(TaskEvent event);

    List<TaskEvent> events(long tenantId, long taskId, long afterSequence, int limit);

    Optional<TaskEvent> latestEvent(long tenantId, long taskId, String type);

    Optional<TaskAuthorizationSnapshot> authorization(long tenantId, long taskId);

    Optional<ToolInvocation> toolInvocation(long tenantId, long taskId, int callSequence);

    boolean claimToolInvocation(ToolInvocation invocation);

    void completeToolInvocation(long tenantId, long taskId, int callSequence,
            JsonNode result, java.time.Instant finishedAt);

    void saveFeedback(long tenantId, long taskId, long memberId, int rating, String comment);

    default JsonNode latestResult(long tenantId, long taskId) {
        return latestEvent(tenantId, taskId, "result").map(TaskEvent::payload).orElse(null);
    }
}

