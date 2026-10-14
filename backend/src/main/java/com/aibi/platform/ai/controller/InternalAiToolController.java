package com.aibi.platform.ai.controller;

import com.aibi.platform.ai.dto.AiDtos.InternalQueryRequest;
import com.aibi.platform.ai.dto.AiDtos.SemanticCatalogData;
import com.aibi.platform.ai.dto.AiDtos.SemanticSearchRequest;
import com.aibi.platform.ai.manager.AiDelegationManager;
import com.aibi.platform.ai.service.AiInternalToolService;
import com.aibi.platform.ai.service.AiSemanticToolService;
import com.aibi.platform.query.dto.QueryDtos.QueryResult;
import com.aibi.platform.query.dto.QueryDtos.ValidationData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
@Hidden
public class InternalAiToolController {

    private final AiDelegationManager delegation;
    private final AiSemanticToolService semantics;
    private final AiInternalToolService queries;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public InternalAiToolController(AiDelegationManager delegation,
            AiSemanticToolService semantics, AiInternalToolService queries,
            ObjectMapper objectMapper, Validator validator) {
        this.delegation = delegation;
        this.semantics = semantics;
        this.queries = queries;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping(value = "/semantic/authorized", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SemanticCatalogData semantics(HttpServletRequest servletRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody byte[] body) {
        verify(servletRequest, body);
        SemanticSearchRequest request = read(body, SemanticSearchRequest.class);
        requireToolCallId(idempotencyKey, request.toolCallId());
        return semantics.authorized(parseTaskId(request.taskId()), request.datasetId());
    }

    @PostMapping(value = "/queries/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ValidationData validate(HttpServletRequest servletRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody byte[] body) {
        verify(servletRequest, body);
        InternalQueryRequest request = validated(read(body, InternalQueryRequest.class));
        requireToolCallId(idempotencyKey, request.toolCallId());
        return queries.validate(parseTaskId(request.taskId()), request.plan(), request.toolCallId());
    }

    @PostMapping(value = "/queries/execute", consumes = MediaType.APPLICATION_JSON_VALUE)
    public QueryResult execute(HttpServletRequest servletRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody byte[] body) {
        verify(servletRequest, body);
        InternalQueryRequest request = validated(read(body, InternalQueryRequest.class));
        requireToolCallId(idempotencyKey, request.toolCallId());
        return queries.execute(parseTaskId(request.taskId()), request.plan(), request.toolCallId());
    }

    private void verify(HttpServletRequest request, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        java.util.Collections.list(request.getHeaderNames()).forEach(name ->
                headers.addAll(name, java.util.Collections.list(request.getHeaders(name))));
        delegation.verify(request.getMethod(), request.getRequestURI(), body, headers);
    }

    private <T> T read(byte[] body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (java.io.IOException exception) {
            throw new com.aibi.platform.common.exception.BadRequestException("内部工具请求格式无效");
        }
    }

    private <T> T validated(T value) {
        var violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String message = violations.stream().map(item -> item.getPropertyPath() + " " + item.getMessage())
                    .collect(Collectors.joining("; "));
            throw new com.aibi.platform.common.exception.BadRequestException(message);
        }
        return value;
    }

    private long parseTaskId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the common safe error.
        }
        throw new com.aibi.platform.common.exception.BadRequestException("task_id 格式错误");
    }

    private void requireToolCallId(String header, String signedBodyValue) {
        if (signedBodyValue == null || !signedBodyValue.equals(header)) {
            throw new com.aibi.platform.common.exception.BadRequestException(
                    "工具幂等键与签名请求体不一致");
        }
    }
}

