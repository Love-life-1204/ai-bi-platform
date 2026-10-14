package com.aibi.platform.query.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.common.json.JsonResourceIds;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Public, SQL-free contracts for the unified query service. */
public final class QueryDtos {

    private QueryDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record QueryPlan(
            @JsonAlias("dataset_version_id") @Positive @JsonResourceId long datasetVersionId,
            @JsonAlias("metric_version_ids") @NotEmpty @Size(max = 20)
            @JsonResourceIds List<@Positive Long> metricVersionIds,
            @JsonAlias("dimension_field_ids") @Size(max = 20)
            @JsonResourceIds List<@Positive Long> dimensionFieldIds,
            @JsonAlias("time_range") @Valid TimeRange timeRange,
            @Size(max = 50) List<@Valid Filter> filters,
            @Size(max = 10) List<@Valid Sort> sort,
            @Min(1) @Max(10000) int limit) {

        public QueryPlan {
            metricVersionIds = metricVersionIds == null ? List.of() : List.copyOf(metricVersionIds);
            dimensionFieldIds = dimensionFieldIds == null ? List.of() : List.copyOf(dimensionFieldIds);
            filters = filters == null ? List.of() : List.copyOf(filters);
            sort = sort == null ? List.of() : List.copyOf(sort);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record TimeRange(
            @JsonAlias("field_id") @Positive @JsonResourceId long fieldId,
            @NotNull OffsetDateTime start,
            @NotNull OffsetDateTime end) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Filter(
            @JsonAlias("field_id") @Positive @JsonResourceId long fieldId,
            @NotBlank @Pattern(regexp = "EQ|NE|GT|GTE|LT|LTE|IN|NOT_IN|IS_NULL|IS_NOT_NULL")
            String operator,
            JsonNode value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Sort(
            @NotBlank @Pattern(regexp = "FIELD|METRIC") String targetType,
            @JsonAlias("target_id") @Positive @JsonResourceId long targetId,
            @NotBlank @Pattern(regexp = "ASC|DESC") String direction) {
    }

    public record ValidationData(boolean valid, String planHash,
            @JsonResourceId long datasetVersionId, @JsonResourceIds List<Long> metricVersionIds, List<String> warnings) {
    }

    public record QueryResult(String queryId, String status, List<Column> columns,
            List<Map<String, Object>> rows, long rowCount, boolean cached,
            PublicationSnapshot publication, long executionMs) {
    }

    public record Column(String key, String label, String dataType, String kind) {
    }

    public record PublicationSnapshot(@JsonResourceId long generationId, long committedPublishSeq,
            long qualityVersion) {
    }

    public record QueryStatusData(String queryId, String status, @JsonResourceId long datasetVersionId,
            @JsonResourceIds List<Long> metricVersionIds, PublicationSnapshot publication,
            long rowCount, String errorCode, String cancelStatus,
            Instant createdAt, Instant startedAt, Instant finishedAt) {
    }

    public record CancelData(String queryId, String status, String cancelStatus) {
    }
}
