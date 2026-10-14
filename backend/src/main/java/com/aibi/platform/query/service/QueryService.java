package com.aibi.platform.query.service;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.api.TraceIdContext;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.common.exception.NotFoundException;
import com.aibi.platform.identity.service.PermissionContextService;
import com.aibi.platform.query.config.QueryProperties;
import com.aibi.platform.query.dto.QueryDtos.CancelData;
import com.aibi.platform.query.dto.QueryDtos.PublicationSnapshot;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.dto.QueryDtos.QueryResult;
import com.aibi.platform.query.dto.QueryDtos.QueryStatusData;
import com.aibi.platform.query.dto.QueryDtos.ValidationData;
import com.aibi.platform.query.engine.QueryEngine;
import com.aibi.platform.query.manager.QueryAuthorizationManager;
import com.aibi.platform.query.manager.QueryAsyncExecutor;
import com.aibi.platform.query.manager.QueryPlanCompiler;
import com.aibi.platform.query.manager.QueryResourceManager;
import com.aibi.platform.query.model.AuthorizedQuery;
import com.aibi.platform.query.model.QueryModels.CompiledQuery;
import com.aibi.platform.query.model.QueryModels.EngineResult;
import com.aibi.platform.query.model.QueryModels.QueryTask;
import com.aibi.platform.query.store.QueryAuditRepository;
import com.aibi.platform.query.store.QueryResultCache;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class QueryService {

    private final QueryAuthorizationManager authorizationManager;
    private final QueryPlanCompiler compiler;
    private final QueryEngine engine;
    private final QueryResourceManager resources;
    private final QueryAsyncExecutor asyncExecutor;
    private final QueryResultCache cache;
    private final QueryAuditRepository audit;
    private final PermissionContextService permissionContext;
    private final QueryProperties properties;
    private final ConcurrentMap<String, QueryTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, QueryResult> results = new ConcurrentHashMap<>();

    public QueryService(QueryAuthorizationManager authorizationManager,
            QueryPlanCompiler compiler, QueryEngine engine, QueryResourceManager resources,
            QueryAsyncExecutor asyncExecutor, QueryResultCache cache, QueryAuditRepository audit,
            PermissionContextService permissionContext, QueryProperties properties) {
        this.authorizationManager = authorizationManager;
        this.compiler = compiler;
        this.engine = engine;
        this.resources = resources;
        this.asyncExecutor = asyncExecutor;
        this.cache = cache;
        this.audit = audit;
        this.permissionContext = permissionContext;
        this.properties = properties;
    }

    public ValidationData validate(String authorization, long tenantId, QueryPlan plan) {
        AuthorizedQuery query = authorizationManager.authorize(authorization, tenantId, plan);
        compiler.compile(query);
        return new ValidationData(true, query.planHash(), query.datasetVersion().id(),
                query.metricVersions().stream().map(value -> value.id()).toList(), List.of());
    }

    public QueryResult execute(String authorization, long tenantId, QueryPlan plan) {
        PreparedQuery prepared = prepare(authorization, tenantId, plan);
        return run(prepared);
    }

    public QueryStatusData submit(String authorization, long tenantId, QueryPlan plan) {
        PreparedQuery prepared = prepare(authorization, tenantId, plan);
        String traceId = TraceIdContext.currentTraceId();
        try {
            asyncExecutor.submit(() -> {
                try {
                    MDC.put(TraceIdContext.MDC_KEY, traceId);
                    run(prepared);
                } catch (ApiException ignored) {
                    // Task state and safe error classification were persisted by run().
                } finally {
                    MDC.remove(TraceIdContext.MDC_KEY);
                }
            });
        } catch (ApiException exception) {
            finish(prepared.task(), "FAILED", 0, exception.getErrorCode().getCode(),
                    "NOT_REQUESTED", 0);
            throw exception;
        }
        return status(prepared.task());
    }

    private PreparedQuery prepare(String authorization, long tenantId, QueryPlan plan) {
        AuthorizedQuery query = authorizationManager.authorize(authorization, tenantId, plan);
        CompiledQuery compiled = compiler.compile(query);
        String queryId = "qry_" + UUID.randomUUID().toString().replace("-", "");
        PublicationSnapshot publication = new PublicationSnapshot(
                query.publication().activeGeneration(), query.publication().committedPublishSeq(),
                query.publication().qualityVersion());
        QueryTask task = new QueryTask(queryId, tenantId, query.dataset().workspaceId(),
                query.memberId(), query.tenantPermissionVersion(), query.memberPermissionVersion(),
                query.datasetVersion().id(), query.metricVersions().stream()
                        .map(value -> value.id()).toList(), publication, query.planHash(),
                "PENDING", 0, null, "NOT_REQUESTED", Instant.now(), null, null,
                0, compiled.sqlDigest());
        tasks.put(queryId, task);
        audit.created(task, plan);
        return new PreparedQuery(authorization, query, compiled, task, publication, cacheKey(query));
    }

    private QueryResult run(PreparedQuery prepared) {
        String authorization = prepared.authorization();
        AuthorizedQuery query = prepared.query();
        CompiledQuery compiled = prepared.compiled();
        QueryTask task = prepared.task();
        PublicationSnapshot publication = prepared.publication();
        String queryId = task.queryId();
        long tenantId = task.tenantId();
        String cacheKey = prepared.cacheKey();
        QueryTask currentBeforeStart = tasks.get(queryId);
        if (currentBeforeStart != null && "CANCELLED".equals(currentBeforeStart.status())) {
            throw new ApiException(ApiErrorCode.QUERY_CANCELLED);
        }
        QueryResult cached = cache.get(cacheKey).orElse(null);
        if (cached != null) {
            QueryResult response = new QueryResult(queryId, "SUCCEEDED", cached.columns(),
                    cached.rows(), cached.rowCount(), true, publication, 0);
            finish(task, "SUCCEEDED", response.rowCount(), null, "NOT_REQUESTED", 0);
            results.put(queryId, response);
            return response;
        }
        Instant started = Instant.now();
        task = task.withStatus("RUNNING", 0, null, "NOT_REQUESTED", started,
                null, 0, compiled.sqlDigest());
        tasks.put(queryId, task);
        audit.changed(task);
        try (QueryResourceManager.Lease ignored = resources.acquire(tenantId)) {
            EngineResult engineResult = engine.execute(queryId, compiled, properties.getTimeoutSeconds());
            reauthorize(authorization, task);
            QueryResult response = new QueryResult(queryId, "SUCCEEDED", compiled.columns(),
                    engineResult.rows(), engineResult.rows().size(), false, publication,
                    engineResult.elapsedMs());
            finish(task, "SUCCEEDED", response.rowCount(), null, "NOT_REQUESTED",
                    engineResult.elapsedMs());
            results.put(queryId, response);
            cache.put(cacheKey, response);
            return response;
        } catch (ApiException exception) {
            QueryTask current = tasks.get(queryId);
            boolean cancelled = current != null && "REQUESTED".equals(current.cancelStatus());
            String errorCode = cancelled ? ApiErrorCode.QUERY_CANCELLED.getCode()
                    : exception.getErrorCode().getCode();
            finish(task, cancelled ? "CANCELLED" : "FAILED", 0, errorCode,
                    cancelled ? "CONFIRMED" : task.cancelStatus(), 0);
            if (cancelled) {
                throw new ApiException(ApiErrorCode.QUERY_CANCELLED);
            }
            throw exception;
        } catch (RuntimeException exception) {
            finish(task, "FAILED", 0, ApiErrorCode.QUERY_EXECUTION_FAILED.getCode(),
                    task.cancelStatus(), 0);
            throw new ApiException(ApiErrorCode.QUERY_EXECUTION_FAILED);
        }
    }

    public QueryStatusData status(String authorization, long tenantId, String queryId) {
        QueryTask task = authorizedTask(authorization, tenantId, queryId);
        return status(task);
    }

    public QueryResult result(String authorization, long tenantId, String queryId) {
        QueryTask task = authorizedTask(authorization, tenantId, queryId);
        if (!"SUCCEEDED".equals(task.status())) {
            throw new ConflictException("查询尚未成功完成");
        }
        QueryResult result = results.get(queryId);
        if (result == null) {
            throw new NotFoundException("查询结果已过期，请重新执行");
        }
        return result;
    }

    public CancelData cancel(String authorization, long tenantId, String queryId) {
        QueryTask task = authorizedTask(authorization, tenantId, queryId);
        if (List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(task.status())) {
            return new CancelData(queryId, task.status(), "NOT_RUNNING");
        }
        QueryTask requested = task.withStatus(task.status(), task.rowCount(), task.errorCode(),
                "REQUESTED", task.startedAt(), task.finishedAt(), task.executionMs(), task.sqlDigest());
        tasks.put(queryId, requested);
        boolean submitted = engine.cancel(queryId);
        if (!submitted && "PENDING".equals(task.status())) {
            finish(requested, "CANCELLED", 0, ApiErrorCode.QUERY_CANCELLED.getCode(),
                    "CONFIRMED", 0);
            return new CancelData(queryId, "CANCELLED", "CONFIRMED");
        }
        audit.changed(requested);
        return new CancelData(queryId, requested.status(), submitted ? "REQUESTED" : "NOT_RUNNING");
    }

    private QueryTask authorizedTask(String authorization, long tenantId, String queryId) {
        QueryTask task = tasks.get(queryId);
        if (task == null || task.tenantId() != tenantId) {
            throw new NotFoundException("查询任务不存在");
        }
        reauthorize(authorization, task);
        return task;
    }

    private void reauthorize(String authorization, QueryTask task) {
        var claims = permissionContext.requireContext(authorization, task.tenantId());
        if (claims.memberId() != task.memberId()
                || claims.tenantPermissionVersion() != task.tenantPermissionVersion()
                || claims.memberPermissionVersion() != task.memberPermissionVersion()) {
            throw new com.aibi.platform.common.exception.ForbiddenException();
        }
    }

    private void finish(QueryTask original, String state, long rowCount,
            String errorCode, String cancelStatus, long executionMs) {
        QueryTask latest = tasks.getOrDefault(original.queryId(), original);
        QueryTask finished = latest.withStatus(state, rowCount, errorCode, cancelStatus,
                latest.startedAt(), Instant.now(), executionMs, original.sqlDigest());
        tasks.put(original.queryId(), finished);
        audit.changed(finished);
    }

    private QueryStatusData status(QueryTask task) {
        return new QueryStatusData(task.queryId(), task.status(), task.datasetVersionId(),
                task.metricVersionIds(), task.publication(), task.rowCount(), task.errorCode(),
                task.cancelStatus(), task.createdAt(), task.startedAt(), task.finishedAt());
    }

    private String cacheKey(AuthorizedQuery query) {
        String timezone = query.metricVersions().stream().map(value ->
                value.timeSpec().path("timezone").asText()).sorted().toList().toString();
        return authorizationManager.hash(Map.ofEntries(
                Map.entry("tenant", query.tenantId()),
                Map.entry("member", query.memberId()),
                Map.entry("tenantPermission", query.tenantPermissionVersion()),
                Map.entry("memberPermission", query.memberPermissionVersion()),
                Map.entry("datasetVersion", query.datasetVersion().id()),
                Map.entry("metrics", query.plan().metricVersionIds()),
                Map.entry("generation", query.publication().activeGeneration()),
                Map.entry("publishSeq", query.publication().committedPublishSeq()),
                Map.entry("qualityVersion", query.publication().qualityVersion()),
                Map.entry("plan", query.planHash()),
                Map.entry("timezone", timezone),
                Map.entry("policy", query.policyHash())));
    }

    private record PreparedQuery(String authorization, AuthorizedQuery query,
            CompiledQuery compiled, QueryTask task, PublicationSnapshot publication,
            String cacheKey) {
    }
}
