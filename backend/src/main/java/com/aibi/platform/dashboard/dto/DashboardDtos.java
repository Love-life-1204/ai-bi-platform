package com.aibi.platform.dashboard.dto;

import com.aibi.platform.common.json.JsonResourceDocument;
import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.dashboard.model.DashboardModels.Chart;
import com.aibi.platform.dashboard.model.DashboardModels.ChartVersion;
import com.aibi.platform.dashboard.model.DashboardModels.Dashboard;
import com.aibi.platform.dashboard.model.DashboardModels.DashboardVersion;
import com.aibi.platform.query.dto.QueryDtos.Filter;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.aibi.platform.query.dto.QueryDtos.QueryResult;
import com.aibi.platform.query.dto.QueryDtos.TimeRange;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Public contracts for the traditional BI visualization workflow. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateChartRequest(
            @JsonAlias("workspace_id") @Positive @JsonResourceId long workspaceId,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 200) String name,
            @JsonAlias("dataset_version_id") @Positive @JsonResourceId long datasetVersionId,
            @JsonAlias("query_plan") @Valid @NotNull QueryPlan queryPlan,
            @JsonAlias("chart_spec") @NotNull @JsonResourceDocument(JsonResourceDocument.Kind.CHART_SPEC) JsonNode chartSpec) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record UpdateChartRequest(
            @NotBlank @Size(max = 200) String name,
            @JsonAlias("dataset_version_id") @Positive @JsonResourceId long datasetVersionId,
            @JsonAlias("query_plan") @Valid @NotNull QueryPlan queryPlan,
            @JsonAlias("chart_spec") @NotNull @JsonResourceDocument(JsonResourceDocument.Kind.CHART_SPEC) JsonNode chartSpec,
            @JsonAlias("lock_version") @PositiveOrZero long lockVersion) {
    }

    public record ChartData(Chart chart, ChartVersion current, ChartVersion draft) {
    }

    public record ChartValidationData(boolean valid, List<ValidationIssue> errors) {
    }

    public record ValidationIssue(String path, String code, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateDashboardRequest(
            @JsonAlias("workspace_id") @Positive @JsonResourceId long workspaceId,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull JsonNode layout,
            @NotNull @JsonResourceDocument(JsonResourceDocument.Kind.DASHBOARD_FILTERS) JsonNode filters,
            @Valid @Size(max = 100) List<WidgetInput> widgets) {

        public CreateDashboardRequest {
            widgets = widgets == null ? List.of() : List.copyOf(widgets);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record UpdateDashboardRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull JsonNode layout,
            @NotNull @JsonResourceDocument(JsonResourceDocument.Kind.DASHBOARD_FILTERS) JsonNode filters,
            @Valid @Size(max = 100) List<WidgetInput> widgets,
            @JsonAlias("lock_version") @PositiveOrZero long lockVersion) {

        public UpdateDashboardRequest {
            widgets = widgets == null ? List.of() : List.copyOf(widgets);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record WidgetInput(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,63}") String key,
            @JsonAlias("chart_version_id") @Positive @JsonResourceId long chartVersionId,
            @NotNull JsonNode position) {
    }

    public record DashboardData(Dashboard dashboard, DashboardVersion current,
            DashboardVersion draft) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record DashboardQueryRequest(
            @JsonAlias("time_range") @Valid TimeRange timeRange,
            @Valid @Size(max = 50) List<Filter> filters,
            @JsonAlias("interaction_filters") @Valid @Size(max = 50)
            List<Filter> interactionFilters,
            @JsonAlias("preview_draft") boolean previewDraft) {

        public DashboardQueryRequest {
            filters = filters == null ? List.of() : List.copyOf(filters);
            interactionFilters = interactionFilters == null
                    ? List.of() : List.copyOf(interactionFilters);
        }
    }

    public record DashboardQueryData(
            @JsonResourceId long dashboardId, @JsonResourceId long dashboardVersionId, String mode,
            List<WidgetResult> widgets) {
    }

    public record WidgetResult(
            String key, @JsonResourceId long chartVersionId, String status, QueryResult result,
            WidgetError error, @JsonResourceDocument(JsonResourceDocument.Kind.CHART_SPEC) JsonNode chartSpec) {
    }

    public record WidgetError(String code, String message, boolean retryable) {
    }

}
