package com.aibi.platform.query.store;

import com.aibi.platform.common.api.TraceIdContext;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.model.QueryModels.QueryTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcQueryAuditRepository implements QueryAuditRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public JdbcQueryAuditRepository(
            @Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void created(QueryTask task, QueryPlan plan) {
        jdbc.update("""
                INSERT INTO bi_query_task
                    (id,tenant_id,workspace_id,member_id,dataset_version_id,request_key,
                     permission_version,generation_id,committed_publish_seq,metric_versions_json,
                     query_plan,plan_hash,engine_query_id,query_status,row_count,created_at,updated_at)
                VALUES (:id,:tenantId,:workspaceId,:memberId,:datasetVersionId,:requestKey,
                        :permissionVersion,:generation,:publishSeq,:metrics,:plan,:planHash,
                        :queryId,:status,0,NOW(3),NOW(3))
                """, base(task).addValue("id", id()).addValue("requestKey", task.queryId())
                .addValue("metrics", json(task.metricVersionIds())).addValue("plan", json(plan)));
        audit(task, "QUERY_SUBMITTED");
    }

    @Override
    public void changed(QueryTask task) {
        jdbc.update("""
                UPDATE bi_query_task
                   SET query_status=:status,started_at=:startedAt,finished_at=:finishedAt,
                       error_code=:errorCode,row_count=:rowCount,updated_at=NOW(3),lock_version=lock_version+1
                 WHERE tenant_id=:tenantId AND engine_query_id=:queryId AND deleted_at IS NULL
                """, base(task).addValue("startedAt", timestamp(task.startedAt()))
                .addValue("finishedAt", timestamp(task.finishedAt()))
                .addValue("errorCode", task.errorCode()).addValue("rowCount", task.rowCount()));
        audit(task, "QUERY_" + task.status());
    }

    private void audit(QueryTask task, String action) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("query_id", task.queryId());
        summary.put("dataset_version_id", task.datasetVersionId());
        summary.put("metric_version_ids", task.metricVersionIds());
        summary.put("generation", task.publication().generationId());
        summary.put("committed_publish_seq", task.publication().committedPublishSeq());
        summary.put("plan_hash", task.planHash());
        summary.put("sql_digest", task.sqlDigest());
        summary.put("execution_ms", task.executionMs());
        summary.put("row_count", task.rowCount());
        summary.put("error_code", task.errorCode());
        summary.put("cancel_status", task.cancelStatus());
        jdbc.update("""
                INSERT INTO sys_audit_event
                    (id,tenant_id,actor_member_id,action_code,resource_type,permission_version,
                     trace_id,result_code,summary_json,occurred_at)
                VALUES (:id,:tenantId,:memberId,:action,'QUERY',:permissionVersion,
                        :traceId,:resultCode,:summary,NOW(3))
                """, new MapSqlParameterSource().addValue("id", id())
                .addValue("tenantId", task.tenantId()).addValue("memberId", task.memberId())
                .addValue("action", action).addValue("permissionVersion", task.tenantPermissionVersion())
                .addValue("traceId", TraceIdContext.currentTraceId()).addValue("resultCode", task.status())
                .addValue("summary", json(summary)));
    }

    private MapSqlParameterSource base(QueryTask task) {
        return new MapSqlParameterSource().addValue("tenantId", task.tenantId())
                .addValue("workspaceId", task.workspaceId()).addValue("memberId", task.memberId())
                .addValue("datasetVersionId", task.datasetVersionId())
                .addValue("permissionVersion", task.tenantPermissionVersion())
                .addValue("generation", task.publication().generationId())
                .addValue("publishSeq", task.publication().committedPublishSeq())
                .addValue("planHash", task.planHash()).addValue("queryId", task.queryId())
                .addValue("status", task.status());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("查询审计序列化失败", exception);
        }
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private long id() {
        return random.nextLong(1, Long.MAX_VALUE);
    }
}
