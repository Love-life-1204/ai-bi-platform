package com.aibi.platform.ai.service;

import com.aibi.platform.ai.mapper.AiStore;
import com.aibi.platform.ai.manager.DelegatedQueryManager;
import com.aibi.platform.ai.model.AiModels.ToolInvocation;
import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.dto.QueryDtos.QueryResult;
import com.aibi.platform.query.dto.QueryDtos.ValidationData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class AiInternalToolService {

    private final AiStore store;
    private final DelegatedQueryManager queries;
    private final ObjectMapper objectMapper;

    public AiInternalToolService(AiStore store, DelegatedQueryManager queries,
            ObjectMapper objectMapper) {
        this.store = store;
        this.queries = queries;
        this.objectMapper = objectMapper;
    }

    public ValidationData validate(long taskId, QueryPlan plan, String toolCallId) {
        InvocationContext context = context(taskId, plan, "query_validate", toolCallId);
        ValidationData authorized = queries.validate(context.snapshot(), plan);
        return invoke(context, ValidationData.class, () -> authorized);
    }

    public QueryResult execute(long taskId, QueryPlan plan, String toolCallId) {
        InvocationContext context = context(taskId, plan, "query_execute", toolCallId);
        queries.validate(context.snapshot(), plan);
        return invoke(context, QueryResult.class,
                () -> queries.execute(taskId, context.snapshot(), plan));
    }

    private InvocationContext context(long taskId, QueryPlan plan,
            String toolName, String toolCallId) {
        var task = store.findTaskByGlobalId(taskId)
                .orElseThrow(() -> new NotFoundException("AI 任务不存在"));
        var snapshot = store.authorization(task.tenantId(), taskId)
                .orElseThrow(() -> new NotFoundException("AI 任务授权快照不存在"));
        String[] parts = toolCallId == null ? new String[0] : toolCallId.split(":", -1);
        if (parts.length != 3 || !parts[0].equals(String.valueOf(taskId))
                || !parts[1].equals(toolName)) {
            throw new BadRequestException("工具幂等键格式错误");
        }
        int sequence;
        try {
            sequence = Integer.parseInt(parts[2]);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("工具幂等键序号错误");
        }
        if (sequence < 1) {
            throw new BadRequestException("工具幂等键序号错误");
        }
        return new InvocationContext(task.tenantId(), taskId, sequence, toolName,
                hash(plan), snapshot);
    }

    private <T> T invoke(InvocationContext context, Class<T> type,
            java.util.function.Supplier<T> operation) {
        ToolInvocation current = store.toolInvocation(context.tenantId(), context.taskId(),
                context.sequence()).orElse(null);
        if (current != null) {
            return existing(current, context, type);
        }
        Instant now = Instant.now();
        ToolInvocation claimed = new ToolInvocation(
                ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
                context.tenantId(), context.taskId(), context.sequence(), context.toolName(),
                context.argumentsHash(), "RUNNING", null, now, null);
        if (!store.claimToolInvocation(claimed)) {
            ToolInvocation raced = store.toolInvocation(context.tenantId(), context.taskId(),
                    context.sequence()).orElseThrow(() -> new ConflictException(
                            "AI 工具调用并发状态不可用"));
            return existing(raced, context, type);
        }
        T result = operation.get();
        store.completeToolInvocation(context.tenantId(), context.taskId(), context.sequence(),
                objectMapper.valueToTree(result), Instant.now());
        return result;
    }

    private <T> T existing(ToolInvocation current, InvocationContext expected, Class<T> type) {
        if (!current.toolName().equals(expected.toolName())
                || !current.argumentsHash().equals(expected.argumentsHash())) {
            throw new ConflictException("工具幂等键对应的参数发生变化");
        }
        if (!"SUCCEEDED".equals(current.status()) || current.result() == null) {
            throw new ConflictException("相同工具调用仍在执行");
        }
        return objectMapper.convertValue(current.result(), type);
    }

    private String hash(QueryPlan plan) {
        try {
            byte[] value = objectMapper.writeValueAsBytes(plan);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("QueryPlan 摘要计算失败", exception);
        }
    }

    private record InvocationContext(
            long tenantId, long taskId, int sequence, String toolName, String argumentsHash,
            com.aibi.platform.identity.dto.TaskAuthorizationSnapshot snapshot) {
    }
}

