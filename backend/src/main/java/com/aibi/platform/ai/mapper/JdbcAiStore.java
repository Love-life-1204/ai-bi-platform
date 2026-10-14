package com.aibi.platform.ai.mapper;

import com.aibi.platform.ai.model.AiModels.Conversation;
import com.aibi.platform.ai.model.AiModels.ConversationContext;
import com.aibi.platform.ai.model.AiModels.Message;
import com.aibi.platform.ai.model.AiModels.Task;
import com.aibi.platform.ai.model.AiModels.TaskEvent;
import com.aibi.platform.ai.model.AiModels.ToolInvocation;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.dto.AccessCheckRequest;
import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcAiStore implements AiStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAiStore(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Conversation insertConversation(Conversation value) {
        jdbc.update("""
                INSERT INTO ai_conversation(id,tenant_id,workspace_id,member_id,title,
                    conversation_status,context_version,created_at,updated_at,lock_version)
                VALUES(:id,:tenant,:workspace,:member,:title,:status,:context,:created,:updated,:lock)
                """, conversationParams(value));
        return value;
    }

    @Override
    public Optional<Conversation> findConversation(long tenantId, long conversationId) {
        return jdbc.query("""
                SELECT * FROM ai_conversation
                WHERE tenant_id=:tenant AND id=:id AND deleted_at IS NULL
                """, Map.of("tenant", tenantId, "id", conversationId), this::conversation).stream().findFirst();
    }

    @Override
    public List<Conversation> listConversations(long tenantId, long memberId) {
        return jdbc.query("""
                SELECT * FROM ai_conversation
                WHERE tenant_id=:tenant AND member_id=:member AND deleted_at IS NULL
                ORDER BY updated_at DESC LIMIT 200
                """, Map.of("tenant", tenantId, "member", memberId), this::conversation);
    }

    @Override
    @MySqlTransactional(timeout = 5)
    public Conversation saveContext(Conversation value, ConversationContext context) {
        int changed = jdbc.update("""
                UPDATE ai_conversation SET context_version=:next,updated_at=:updated,
                    lock_version=lock_version+1
                WHERE tenant_id=:tenant AND id=:id AND context_version=:previous
                """, Map.of("next", context.version(), "updated", timestamp(value.updatedAt()),
                        "tenant", value.tenantId(), "id", value.id(),
                        "previous", context.version() - 1));
        if (changed != 1) {
            throw new com.aibi.platform.common.exception.ConflictException("AI 会话上下文版本冲突");
        }
        jdbc.update("""
                INSERT INTO ai_conversation_context(tenant_id,conversation_id,context_version,
                    context_json,created_at) VALUES(:tenant,:conversation,:version,:json,:created)
                """, Map.of("tenant", context.tenantId(), "conversation", context.conversationId(),
                        "version", context.version(), "json", write(context.value()),
                        "created", timestamp(context.createdAt())));
        return value;
    }

    @Override
    public Optional<ConversationContext> latestContext(long tenantId, long conversationId) {
        return jdbc.query("""
                SELECT * FROM ai_conversation_context
                WHERE tenant_id=:tenant AND conversation_id=:conversation
                ORDER BY context_version DESC LIMIT 1
                """, Map.of("tenant", tenantId, "conversation", conversationId), (rs, row) ->
                    new ConversationContext(tenantId, conversationId,
                            rs.getInt("context_version"), read(rs.getString("context_json")),
                            instant(rs, "created_at"))).stream().findFirst();
    }

    @Override
    public Message appendMessage(Message value) {
        jdbc.update("""
                INSERT INTO ai_message(id,tenant_id,conversation_id,message_seq,message_role,
                    content_redacted,ai_task_id,created_at,updated_at,lock_version)
                VALUES(:id,:tenant,:conversation,:sequence,:role,:content,:task,:created,:created,0)
                """, new MapSqlParameterSource()
                .addValue("id", value.id()).addValue("tenant", value.tenantId())
                .addValue("conversation", value.conversationId()).addValue("sequence", value.sequence())
                .addValue("role", value.role()).addValue("content", value.content())
                .addValue("task", value.taskId()).addValue("created", timestamp(value.createdAt())));
        return value;
    }

    @Override
    public List<Message> listMessages(long tenantId, long conversationId) {
        return jdbc.query("""
                SELECT * FROM ai_message WHERE tenant_id=:tenant AND conversation_id=:conversation
                    AND deleted_at IS NULL ORDER BY message_seq
                """, Map.of("tenant", tenantId, "conversation", conversationId), (rs, row) ->
                    new Message(rs.getLong("id"), tenantId, conversationId,
                            rs.getLong("message_seq"), rs.getString("message_role"),
                            rs.getString("content_redacted"), nullableLong(rs, "ai_task_id"),
                            instant(rs, "created_at")));
    }

    @Override
    @MySqlTransactional(timeout = 5)
    public Task insertTask(Task value, TaskAuthorizationSnapshot snapshot) {
        jdbc.update("""
                INSERT INTO ai_task(id,tenant_id,conversation_id,member_id,request_key,
                    graph_thread_key,permission_version,task_status,deadline_at,attempt,
                    context_version,last_sequence,created_at,updated_at,lock_version)
                VALUES(:id,:tenant,:conversation,:member,:request,:thread,:permission,:status,
                    :deadline,:attempt,:context,:sequence,:created,:created,0)
                """, taskParams(value).addValue("permission", snapshot.tenantPermissionVersion()));
        AccessCheckRequest access = snapshot.accessRequest();
        jdbc.update("""
                INSERT INTO ai_task_auth_snapshot(tenant_id,ai_task_id,session_id,user_id,member_id,
                    tenant_permission_version,member_permission_version,workspace_id,
                    permission_code,asset_id,action_code,created_at)
                VALUES(:tenant,:task,:session,:user,:member,:tenantVersion,:memberVersion,
                    :workspace,:permission,:asset,:action,:created)
                """, new MapSqlParameterSource()
                .addValue("tenant", value.tenantId()).addValue("task", value.id())
                .addValue("session", snapshot.sessionId()).addValue("user", snapshot.userId())
                .addValue("member", snapshot.memberId())
                .addValue("tenantVersion", snapshot.tenantPermissionVersion())
                .addValue("memberVersion", snapshot.memberPermissionVersion())
                .addValue("workspace", access.workspaceId()).addValue("permission", access.permissionCode())
                .addValue("asset", access.assetId()).addValue("action", access.actionCode())
                .addValue("created", timestamp(value.createdAt())));
        return value;
    }

    @Override
    public Optional<Task> findTask(long tenantId, long taskId) {
        return jdbc.query("""
                SELECT * FROM ai_task WHERE tenant_id=:tenant AND id=:id AND deleted_at IS NULL
                """, Map.of("tenant", tenantId, "id", taskId), this::task).stream().findFirst();
    }

    @Override
    public Optional<Task> findTaskByGlobalId(long taskId) {
        return jdbc.query("""
                SELECT * FROM ai_task WHERE id=:id AND deleted_at IS NULL
                """, Map.of("id", taskId), this::task).stream().findFirst();
    }

    @Override
    public Optional<Task> findTaskByRequest(long tenantId, long memberId, String requestKey) {
        return jdbc.query("""
                SELECT * FROM ai_task WHERE tenant_id=:tenant AND member_id=:member
                    AND request_key=:request AND deleted_at IS NULL
                """, Map.of("tenant", tenantId, "member", memberId, "request", requestKey),
                this::task).stream().findFirst();
    }

    @Override
    public Task updateTask(Task value) {
        int changed = jdbc.update("""
                UPDATE ai_task SET task_status=:status,attempt=:attempt,
                    context_version=:context,last_sequence=:sequence,error_code=:error,
                    started_at=:started,finished_at=:finished,updated_at=CURRENT_TIMESTAMP(3),
                    lock_version=lock_version+1
                WHERE tenant_id=:tenant AND id=:id AND deleted_at IS NULL
                """, taskParams(value));
        if (changed != 1) {
            throw new NotFoundException("AI 任务不存在");
        }
        return value;
    }

    @Override
    public TaskEvent appendEvent(TaskEvent value) {
        jdbc.update("""
                INSERT INTO ai_task_event(tenant_id,ai_task_id,sequence,event_type,trace_id,
                    payload_json,emitted_at,retention_at)
                VALUES(:tenant,:task,:sequence,:type,:trace,:payload,:emitted,
                    DATE_ADD(:emitted, INTERVAL 30 DAY))
                ON DUPLICATE KEY UPDATE event_type=VALUES(event_type)
                """, Map.of("tenant", value.tenantId(), "task", value.taskId(),
                        "sequence", value.sequence(), "type", value.type(), "trace", value.traceId(),
                        "payload", write(value.payload()), "emitted", timestamp(value.emittedAt())));
        return value;
    }

    @Override
    public List<TaskEvent> events(long tenantId, long taskId, long afterSequence, int limit) {
        return jdbc.query("""
                SELECT * FROM ai_task_event WHERE tenant_id=:tenant AND ai_task_id=:task
                    AND sequence>:after ORDER BY sequence LIMIT :limit
                """, Map.of("tenant", tenantId, "task", taskId, "after", afterSequence,
                        "limit", Math.min(Math.max(limit, 1), 500)), this::event);
    }

    @Override
    public Optional<TaskEvent> latestEvent(long tenantId, long taskId, String type) {
        return jdbc.query("""
                SELECT * FROM ai_task_event WHERE tenant_id=:tenant AND ai_task_id=:task
                    AND event_type=:type ORDER BY sequence DESC LIMIT 1
                """, Map.of("tenant", tenantId, "task", taskId, "type", type), this::event)
                .stream().findFirst();
    }

    @Override
    public Optional<TaskAuthorizationSnapshot> authorization(long tenantId, long taskId) {
        return jdbc.query("""
                SELECT * FROM ai_task_auth_snapshot WHERE tenant_id=:tenant AND ai_task_id=:task
                """, Map.of("tenant", tenantId, "task", taskId), (rs, row) ->
                    new TaskAuthorizationSnapshot(rs.getLong("session_id"), rs.getLong("user_id"),
                            tenantId, rs.getLong("member_id"),
                            rs.getLong("tenant_permission_version"),
                            rs.getLong("member_permission_version"),
                            new AccessCheckRequest(nullableLong(rs, "workspace_id"),
                                    rs.getString("permission_code"), nullableLong(rs, "asset_id"),
                                    rs.getString("action_code")))).stream().findFirst();
    }

    @Override
    public Optional<ToolInvocation> toolInvocation(long tenantId, long taskId, int callSequence) {
        return jdbc.query("""
                SELECT * FROM ai_tool_invocation
                WHERE tenant_id=:tenant AND ai_task_id=:task AND call_seq=:sequence
                    AND deleted_at IS NULL
                """, Map.of("tenant", tenantId, "task", taskId, "sequence", callSequence),
                (rs, row) -> new ToolInvocation(rs.getLong("id"), tenantId, taskId,
                        rs.getInt("call_seq"), rs.getString("tool_name"),
                        rs.getString("arguments_hash"), rs.getString("invocation_status"),
                        readNullable(rs.getString("result_summary")),
                        instant(rs, "started_at"), instant(rs, "finished_at")))
                .stream().findFirst();
    }

    @Override
    public boolean claimToolInvocation(ToolInvocation value) {
        try {
            jdbc.update("""
                    INSERT INTO ai_tool_invocation(id,tenant_id,ai_task_id,call_seq,tool_name,
                        arguments_hash,invocation_status,started_at,created_at,updated_at,lock_version)
                    VALUES(:id,:tenant,:task,:sequence,:tool,:hash,'RUNNING',
                        :started,:started,:started,0)
                    """, Map.of("id", value.id(), "tenant", value.tenantId(),
                            "task", value.taskId(), "sequence", value.callSequence(),
                            "tool", value.toolName(), "hash", value.argumentsHash(),
                            "started", timestamp(value.startedAt())));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public void completeToolInvocation(long tenantId, long taskId, int callSequence,
            JsonNode result, Instant finishedAt) {
        int changed = jdbc.update("""
                UPDATE ai_tool_invocation SET invocation_status='SUCCEEDED',
                    result_summary=:result,finished_at=:finished,updated_at=:finished,
                    lock_version=lock_version+1
                WHERE tenant_id=:tenant AND ai_task_id=:task AND call_seq=:sequence
                    AND invocation_status='RUNNING' AND deleted_at IS NULL
                """, Map.of("result", write(result), "finished", timestamp(finishedAt),
                        "tenant", tenantId, "task", taskId, "sequence", callSequence));
        if (changed != 1) {
            throw new com.aibi.platform.common.exception.ConflictException(
                    "AI 工具调用状态冲突");
        }
    }

    @Override
    public void saveFeedback(long tenantId, long taskId, long memberId, int rating, String comment) {
        jdbc.update("""
                INSERT INTO ai_feedback(id,tenant_id,ai_task_id,member_id,rating,comment_redacted)
                VALUES(:id,:tenant,:task,:member,:rating,:comment)
                ON DUPLICATE KEY UPDATE rating=VALUES(rating),comment_redacted=VALUES(comment_redacted)
                """, Map.of("id", ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
                        "tenant", tenantId, "task", taskId, "member", memberId,
                        "rating", rating, "comment", comment == null ? "" : comment));
    }

    private MapSqlParameterSource conversationParams(Conversation value) {
        return new MapSqlParameterSource().addValue("id", value.id())
                .addValue("tenant", value.tenantId()).addValue("workspace", value.workspaceId())
                .addValue("member", value.memberId()).addValue("title", value.title())
                .addValue("status", value.status()).addValue("context", value.contextVersion())
                .addValue("created", timestamp(value.createdAt())).addValue("updated", timestamp(value.updatedAt()))
                .addValue("lock", value.lockVersion());
    }

    private MapSqlParameterSource taskParams(Task value) {
        return new MapSqlParameterSource().addValue("id", value.id())
                .addValue("tenant", value.tenantId()).addValue("conversation", value.conversationId())
                .addValue("member", value.memberId()).addValue("request", value.requestKey())
                .addValue("thread", value.graphThreadKey()).addValue("context", value.contextVersion())
                .addValue("status", value.status()).addValue("attempt", value.attempt())
                .addValue("sequence", value.lastSequence()).addValue("error", value.errorCode())
                .addValue("deadline", timestamp(value.deadlineAt())).addValue("created", timestamp(value.createdAt()))
                .addValue("started", timestamp(value.startedAt())).addValue("finished", timestamp(value.finishedAt()));
    }

    private Conversation conversation(ResultSet rs, int row) throws SQLException {
        return new Conversation(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getLong("workspace_id"), rs.getLong("member_id"), rs.getString("title"),
                rs.getString("conversation_status"), rs.getInt("context_version"),
                instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("lock_version"));
    }

    private Task task(ResultSet rs, int row) throws SQLException {
        return new Task(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("conversation_id"),
                rs.getLong("member_id"), rs.getString("request_key"), rs.getString("graph_thread_key"),
                rs.getInt("context_version"), rs.getString("task_status"), rs.getInt("attempt"),
                rs.getLong("last_sequence"), rs.getString("error_code"), instant(rs, "deadline_at"),
                instant(rs, "created_at"), instant(rs, "started_at"), instant(rs, "finished_at"));
    }

    private TaskEvent event(ResultSet rs, int row) throws SQLException {
        return new TaskEvent(rs.getLong("tenant_id"), rs.getLong("ai_task_id"),
                rs.getLong("sequence"), rs.getString("event_type"), rs.getString("trace_id"),
                read(rs.getString("payload_json")), instant(rs, "emitted_at"));
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI JSON 序列化失败", exception);
        }
    }

    private JsonNode read(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 持久化 JSON 无效", exception);
        }
    }

    private JsonNode readNullable(String value) {
        return value == null ? null : read(value);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}

