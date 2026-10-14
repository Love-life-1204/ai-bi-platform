package com.aibi.platform.query.model;

import com.aibi.platform.query.dto.QueryDtos.Column;
import com.aibi.platform.query.dto.QueryDtos.PublicationSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Internal immutable query execution records. */
public final class QueryModels {

    private QueryModels() {
    }

    public record BoundParameter(Object value, int jdbcType) {
    }

    public record CompiledQuery(String sql, List<BoundParameter> parameters,
            List<Column> columns, String sqlDigest) {
    }

    public record EngineResult(List<Map<String, Object>> rows, long elapsedMs) {
    }

    public record QueryTask(String queryId, long tenantId, long workspaceId,
            long memberId, long tenantPermissionVersion, long memberPermissionVersion,
            long datasetVersionId, List<Long> metricVersionIds,
            PublicationSnapshot publication, String planHash, String status,
            long rowCount, String errorCode, String cancelStatus, Instant createdAt,
            Instant startedAt, Instant finishedAt, long executionMs, String sqlDigest) {

        public QueryTask withStatus(String nextStatus, long nextRowCount,
                String nextErrorCode, String nextCancelStatus, Instant nextStartedAt,
                Instant nextFinishedAt, long nextExecutionMs, String nextSqlDigest) {
            return new QueryTask(queryId, tenantId, workspaceId, memberId,
                    tenantPermissionVersion, memberPermissionVersion, datasetVersionId,
                    metricVersionIds, publication, planHash, nextStatus, nextRowCount,
                    nextErrorCode, nextCancelStatus, createdAt, nextStartedAt,
                    nextFinishedAt, nextExecutionMs, nextSqlDigest);
        }
    }
}
