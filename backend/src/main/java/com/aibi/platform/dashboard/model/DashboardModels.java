package com.aibi.platform.dashboard.model;

import com.aibi.platform.common.json.JsonResourceDocument;
import com.aibi.platform.common.json.JsonResourceId;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** Persistent chart and dashboard models. Published versions are immutable. */
public final class DashboardModels {

    private DashboardModels() {
    }

    public record Chart(
            @JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long workspaceId, String code, String name,
            @JsonResourceId long ownerMemberId, @JsonResourceId Long currentVersionId, String status,
            Instant createdAt, Instant updatedAt, long lockVersion) {
    }

    public record ChartVersion(
            @JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long chartId, int versionNo, @JsonResourceId long datasetVersionId,
            QueryPlan queryPlan, @JsonResourceDocument(JsonResourceDocument.Kind.CHART_SPEC) JsonNode chartSpec, String status,
            Instant publishedAt, long lockVersion) {
    }

    public record Dashboard(
            @JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long workspaceId, String code, String name,
            @JsonResourceId long ownerMemberId, @JsonResourceId Long currentVersionId, String status,
            Instant createdAt, Instant updatedAt, long lockVersion) {
    }

    public record DashboardVersion(
            @JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long dashboardId, int versionNo,
            JsonNode layout, @JsonResourceDocument(JsonResourceDocument.Kind.DASHBOARD_FILTERS) JsonNode filters, List<DashboardWidget> widgets,
            String status, Instant publishedAt, long lockVersion) {

        public DashboardVersion {
            widgets = widgets == null ? List.of() : List.copyOf(widgets);
        }
    }

    public record DashboardWidget(
            @JsonResourceId long id, @JsonResourceId long tenantId, @JsonResourceId long dashboardVersionId, @JsonResourceId long chartVersionId,
            String key, JsonNode position, long lockVersion) {
    }
}
