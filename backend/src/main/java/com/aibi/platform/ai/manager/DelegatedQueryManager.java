package com.aibi.platform.ai.manager;

import com.aibi.platform.identity.dto.TaskAuthorizationSnapshot;
import com.aibi.platform.identity.service.TaskAuthorizationService;
import com.aibi.platform.query.config.QueryProperties;
import com.aibi.platform.query.dto.QueryDtos.PublicationSnapshot;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.dto.QueryDtos.QueryResult;
import com.aibi.platform.query.dto.QueryDtos.ValidationData;
import com.aibi.platform.query.engine.QueryEngine;
import com.aibi.platform.query.manager.QueryAuthorizationManager;
import com.aibi.platform.query.manager.QueryPlanCompiler;
import com.aibi.platform.query.manager.QueryResourceManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class DelegatedQueryManager {

    private final QueryAuthorizationManager authorization;
    private final QueryPlanCompiler compiler;
    private final QueryEngine engine;
    private final QueryResourceManager resources;
    private final QueryProperties properties;
    private final TaskAuthorizationService taskAuthorization;
    private final ConcurrentMap<Long, String> runningQueries = new ConcurrentHashMap<>();

    public DelegatedQueryManager(QueryAuthorizationManager authorization,
            QueryPlanCompiler compiler, QueryEngine engine, QueryResourceManager resources,
            QueryProperties properties, TaskAuthorizationService taskAuthorization) {
        this.authorization = authorization;
        this.compiler = compiler;
        this.engine = engine;
        this.resources = resources;
        this.properties = properties;
        this.taskAuthorization = taskAuthorization;
    }

    public ValidationData validate(TaskAuthorizationSnapshot snapshot, QueryPlan plan) {
        var query = authorization.authorize(snapshot, plan);
        compiler.compile(query);
        return new ValidationData(true, query.planHash(), query.datasetVersion().id(),
                query.metricVersions().stream().map(value -> value.id()).toList(), List.of());
    }

    public QueryResult execute(long aiTaskId, TaskAuthorizationSnapshot snapshot, QueryPlan plan) {
        var query = authorization.authorize(snapshot, plan);
        var compiled = compiler.compile(query);
        String queryId = "qry_" + UUID.randomUUID().toString().replace("-", "");
        runningQueries.put(aiTaskId, queryId);
        try (QueryResourceManager.Lease ignored = resources.acquire(snapshot.tenantId())) {
            var result = engine.execute(queryId, compiled, properties.getTimeoutSeconds());
            taskAuthorization.reauthorize(snapshot);
            PublicationSnapshot publication = new PublicationSnapshot(
                    query.publication().activeGeneration(), query.publication().committedPublishSeq(),
                    query.publication().qualityVersion());
            return new QueryResult(queryId, "SUCCEEDED", compiled.columns(), result.rows(),
                    result.rows().size(), false, publication, result.elapsedMs());
        } finally {
            runningQueries.remove(aiTaskId, queryId);
        }
    }

    public boolean cancel(long aiTaskId) {
        String queryId = runningQueries.get(aiTaskId);
        return queryId != null && engine.cancel(queryId);
    }
}

