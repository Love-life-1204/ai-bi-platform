package com.aibi.platform.dashboard.mapper;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.dashboard.model.DashboardModels.Chart;
import com.aibi.platform.dashboard.model.DashboardModels.ChartVersion;
import com.aibi.platform.dashboard.model.DashboardModels.Dashboard;
import com.aibi.platform.dashboard.model.DashboardModels.DashboardVersion;
import com.aibi.platform.dashboard.model.DashboardModels.DashboardWidget;
import com.aibi.platform.query.dto.QueryDtos.QueryPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
public class JdbcDashboardMapper implements DashboardMapper {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcDashboardMapper(@Qualifier("mysqlJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Chart insertChart(Chart value) {
        try {
            jdbc.update("""
                    INSERT INTO bi_chart
                        (id,tenant_id,workspace_id,chart_code,chart_name,owner_member_id,
                         current_version_id,chart_status,created_at,updated_at,lock_version)
                    VALUES
                        (:id,:tenantId,:workspaceId,:code,:name,:ownerId,
                         :currentVersionId,:status,:createdAt,:updatedAt,:lockVersion)
                    """, chartParameters(value));
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("图表编码已存在");
        }
    }

    @Override
    public Chart updateChart(Chart value, long expectedLockVersion) {
        int changed = jdbc.update("""
                UPDATE bi_chart SET chart_name=:name,current_version_id=:currentVersionId,
                       chart_status=:status,updated_at=:updatedAt,lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expected
                   AND deleted_at IS NULL
                """, chartParameters(value).addValue("expected", expectedLockVersion));
        optimistic(changed);
        return value;
    }

    @Override
    public Optional<Chart> findChart(long tenantId, long chartId) {
        return jdbc.query(chartSelect() + " AND tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", tenantId, "id", chartId), this::chart).stream().findFirst();
    }

    @Override
    public List<Chart> listCharts(long tenantId, Long workspaceId) {
        String filter = workspaceId == null ? "" : " AND workspace_id=:workspaceId";
        return jdbc.query(chartSelect() + " AND tenant_id=:tenantId" + filter
                        + " ORDER BY chart_code",
                new MapSqlParameterSource("tenantId", tenantId)
                        .addValue("workspaceId", workspaceId), this::chart);
    }

    @Override
    public ChartVersion insertChartVersion(ChartVersion value) {
        try {
            jdbc.update("""
                    INSERT INTO bi_chart_version
                        (id,tenant_id,chart_id,version_no,dataset_version_id,query_plan,
                         chart_spec,version_status,published_at,lock_version)
                    VALUES
                        (:id,:tenantId,:chartId,:versionNo,:datasetVersionId,:queryPlan,
                         :chartSpec,:status,:publishedAt,:lockVersion)
                    """, chartVersionParameters(value));
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("图表版本号已存在");
        }
    }

    @Override
    public ChartVersion updateChartVersion(ChartVersion value, long expectedLockVersion) {
        int changed = jdbc.update("""
                UPDATE bi_chart_version
                   SET dataset_version_id=:datasetVersionId,query_plan=:queryPlan,
                       chart_spec=:chartSpec,version_status=:status,published_at=:publishedAt,
                       updated_at=NOW(3),lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expected
                   AND version_status<>'PUBLISHED' AND deleted_at IS NULL
                """, chartVersionParameters(value).addValue("expected", expectedLockVersion));
        optimistic(changed);
        return value;
    }

    @Override
    public Optional<ChartVersion> findChartVersion(long tenantId, long versionId) {
        return jdbc.query(chartVersionSelect() + " AND tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", tenantId, "id", versionId), this::chartVersion)
                .stream().findFirst();
    }

    @Override
    public List<ChartVersion> listChartVersions(long tenantId, long chartId) {
        return jdbc.query(chartVersionSelect()
                        + " AND tenant_id=:tenantId AND chart_id=:chartId ORDER BY version_no",
                Map.of("tenantId", tenantId, "chartId", chartId), this::chartVersion);
    }

    @Override
    public Dashboard insertDashboard(Dashboard value) {
        try {
            jdbc.update("""
                    INSERT INTO bi_dashboard
                        (id,tenant_id,workspace_id,dashboard_code,dashboard_name,owner_member_id,
                         current_version_id,dashboard_status,created_at,updated_at,lock_version)
                    VALUES
                        (:id,:tenantId,:workspaceId,:code,:name,:ownerId,
                         :currentVersionId,:status,:createdAt,:updatedAt,:lockVersion)
                    """, dashboardParameters(value));
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("看板编码已存在");
        }
    }

    @Override
    public Dashboard updateDashboard(Dashboard value, long expectedLockVersion) {
        int changed = jdbc.update("""
                UPDATE bi_dashboard SET dashboard_name=:name,current_version_id=:currentVersionId,
                       dashboard_status=:status,updated_at=:updatedAt,lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expected
                   AND deleted_at IS NULL
                """, dashboardParameters(value).addValue("expected", expectedLockVersion));
        optimistic(changed);
        return value;
    }

    @Override
    public Optional<Dashboard> findDashboard(long tenantId, long dashboardId) {
        return jdbc.query(dashboardSelect() + " AND tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", tenantId, "id", dashboardId), this::dashboard)
                .stream().findFirst();
    }

    @Override
    public List<Dashboard> listDashboards(long tenantId, Long workspaceId) {
        String filter = workspaceId == null ? "" : " AND workspace_id=:workspaceId";
        return jdbc.query(dashboardSelect() + " AND tenant_id=:tenantId" + filter
                        + " ORDER BY dashboard_code",
                new MapSqlParameterSource("tenantId", tenantId)
                        .addValue("workspaceId", workspaceId), this::dashboard);
    }

    @Override
    public DashboardVersion insertDashboardVersion(DashboardVersion value) {
        try {
            jdbc.update("""
                    INSERT INTO bi_dashboard_version
                        (id,tenant_id,dashboard_id,version_no,layout_json,filters_json,
                         version_status,published_at,lock_version)
                    VALUES
                        (:id,:tenantId,:dashboardId,:versionNo,:layout,:filters,
                         :status,:publishedAt,:lockVersion)
                    """, dashboardVersionParameters(value));
            insertWidgets(value);
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("看板版本号或组件键已存在");
        }
    }

    @Override
    public DashboardVersion updateDashboardVersion(
            DashboardVersion value, long expectedLockVersion) {
        int changed = jdbc.update("""
                UPDATE bi_dashboard_version
                   SET layout_json=:layout,filters_json=:filters,version_status=:status,
                       published_at=:publishedAt,updated_at=NOW(3),lock_version=:lockVersion
                 WHERE tenant_id=:tenantId AND id=:id AND lock_version=:expected
                   AND version_status<>'PUBLISHED' AND deleted_at IS NULL
                """, dashboardVersionParameters(value).addValue("expected", expectedLockVersion));
        optimistic(changed);
        if ("DRAFT".equals(value.status())) {
            jdbc.update("UPDATE bi_dashboard_widget SET deleted_at=NOW(3)"
                            + " WHERE tenant_id=:tenantId AND dashboard_version_id=:id"
                            + " AND deleted_at IS NULL",
                    Map.of("tenantId", value.tenantId(), "id", value.id()));
            insertWidgets(value);
        }
        return value;
    }

    @Override
    public Optional<DashboardVersion> findDashboardVersion(long tenantId, long versionId) {
        return jdbc.query(dashboardVersionSelect() + " AND tenant_id=:tenantId AND id=:id",
                Map.of("tenantId", tenantId, "id", versionId), this::dashboardVersion)
                .stream().findFirst();
    }

    @Override
    public List<DashboardVersion> listDashboardVersions(long tenantId, long dashboardId) {
        return jdbc.query(dashboardVersionSelect()
                        + " AND tenant_id=:tenantId AND dashboard_id=:dashboardId ORDER BY version_no",
                Map.of("tenantId", tenantId, "dashboardId", dashboardId), this::dashboardVersion);
    }

    private void insertWidgets(DashboardVersion version) {
        for (DashboardWidget widget : version.widgets()) {
            jdbc.update("""
                    INSERT INTO bi_dashboard_widget
                        (id,tenant_id,dashboard_version_id,chart_version_id,widget_key,
                         position_json,lock_version)
                    VALUES
                        (:id,:tenantId,:dashboardVersionId,:chartVersionId,:key,
                         :position,:lockVersion)
                    """, new MapSqlParameterSource()
                    .addValue("id", widget.id()).addValue("tenantId", widget.tenantId())
                    .addValue("dashboardVersionId", version.id())
                    .addValue("chartVersionId", widget.chartVersionId())
                    .addValue("key", widget.key())
                    .addValue("position", json(widget.position()))
                    .addValue("lockVersion", widget.lockVersion()));
        }
    }

    private List<DashboardWidget> widgets(long tenantId, long versionId) {
        return jdbc.query("""
                SELECT id,tenant_id,dashboard_version_id,chart_version_id,widget_key,
                       position_json,lock_version
                  FROM bi_dashboard_widget
                 WHERE tenant_id=:tenantId AND dashboard_version_id=:versionId
                   AND deleted_at IS NULL ORDER BY id
                """, Map.of("tenantId", tenantId, "versionId", versionId), (rs, row) ->
                new DashboardWidget(rs.getLong("id"), rs.getLong("tenant_id"),
                        rs.getLong("dashboard_version_id"), rs.getLong("chart_version_id"),
                        rs.getString("widget_key"), tree(rs.getString("position_json")),
                        rs.getLong("lock_version")));
    }

    private Chart chart(ResultSet rs, int row) throws SQLException {
        return new Chart(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getLong("workspace_id"), rs.getString("chart_code"),
                rs.getString("chart_name"), rs.getLong("owner_member_id"),
                nullableLong(rs, "current_version_id"), rs.getString("chart_status"),
                instant(rs, "created_at"), instant(rs, "updated_at"),
                rs.getLong("lock_version"));
    }

    private ChartVersion chartVersion(ResultSet rs, int row) throws SQLException {
        return new ChartVersion(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getLong("chart_id"), rs.getInt("version_no"),
                rs.getLong("dataset_version_id"), read(rs.getString("query_plan"), QueryPlan.class),
                tree(rs.getString("chart_spec")), rs.getString("version_status"),
                instant(rs, "published_at"), rs.getLong("lock_version"));
    }

    private Dashboard dashboard(ResultSet rs, int row) throws SQLException {
        return new Dashboard(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getLong("workspace_id"), rs.getString("dashboard_code"),
                rs.getString("dashboard_name"), rs.getLong("owner_member_id"),
                nullableLong(rs, "current_version_id"), rs.getString("dashboard_status"),
                instant(rs, "created_at"), instant(rs, "updated_at"),
                rs.getLong("lock_version"));
    }

    private DashboardVersion dashboardVersion(ResultSet rs, int row) throws SQLException {
        long tenantId = rs.getLong("tenant_id");
        long id = rs.getLong("id");
        return new DashboardVersion(id, tenantId, rs.getLong("dashboard_id"),
                rs.getInt("version_no"), tree(rs.getString("layout_json")),
                tree(rs.getString("filters_json")), widgets(tenantId, id),
                rs.getString("version_status"), instant(rs, "published_at"),
                rs.getLong("lock_version"));
    }

    private MapSqlParameterSource chartParameters(Chart value) {
        return commonAssetParameters(value.id(), value.tenantId(), value.workspaceId(),
                value.code(), value.name(), value.ownerMemberId(), value.currentVersionId(),
                value.status(), value.createdAt(), value.updatedAt(), value.lockVersion());
    }

    private MapSqlParameterSource dashboardParameters(Dashboard value) {
        return commonAssetParameters(value.id(), value.tenantId(), value.workspaceId(),
                value.code(), value.name(), value.ownerMemberId(), value.currentVersionId(),
                value.status(), value.createdAt(), value.updatedAt(), value.lockVersion());
    }

    private MapSqlParameterSource commonAssetParameters(long id, long tenantId, long workspaceId,
            String code, String name, long ownerId, Long currentVersionId, String status,
            java.time.Instant createdAt, java.time.Instant updatedAt, long lockVersion) {
        return new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenantId)
                .addValue("workspaceId", workspaceId).addValue("code", code)
                .addValue("name", name).addValue("ownerId", ownerId)
                .addValue("currentVersionId", currentVersionId).addValue("status", status)
                .addValue("createdAt", Timestamp.from(createdAt))
                .addValue("updatedAt", Timestamp.from(updatedAt)).addValue("lockVersion", lockVersion);
    }

    private MapSqlParameterSource chartVersionParameters(ChartVersion value) {
        return new MapSqlParameterSource().addValue("id", value.id())
                .addValue("tenantId", value.tenantId()).addValue("chartId", value.chartId())
                .addValue("versionNo", value.versionNo())
                .addValue("datasetVersionId", value.datasetVersionId())
                .addValue("queryPlan", json(value.queryPlan()))
                .addValue("chartSpec", json(value.chartSpec())).addValue("status", value.status())
                .addValue("publishedAt", timestamp(value.publishedAt()))
                .addValue("lockVersion", value.lockVersion());
    }

    private MapSqlParameterSource dashboardVersionParameters(DashboardVersion value) {
        return new MapSqlParameterSource().addValue("id", value.id())
                .addValue("tenantId", value.tenantId())
                .addValue("dashboardId", value.dashboardId())
                .addValue("versionNo", value.versionNo()).addValue("layout", json(value.layout()))
                .addValue("filters", json(value.filters())).addValue("status", value.status())
                .addValue("publishedAt", timestamp(value.publishedAt()))
                .addValue("lockVersion", value.lockVersion());
    }

    private String chartSelect() {
        return "SELECT id,tenant_id,workspace_id,chart_code,chart_name,owner_member_id,"
                + "current_version_id,chart_status,created_at,updated_at,lock_version"
                + " FROM bi_chart WHERE deleted_at IS NULL";
    }

    private String chartVersionSelect() {
        return "SELECT id,tenant_id,chart_id,version_no,dataset_version_id,query_plan,chart_spec,"
                + "version_status,published_at,lock_version"
                + " FROM bi_chart_version WHERE deleted_at IS NULL";
    }

    private String dashboardSelect() {
        return "SELECT id,tenant_id,workspace_id,dashboard_code,dashboard_name,owner_member_id,"
                + "current_version_id,dashboard_status,created_at,updated_at,lock_version"
                + " FROM bi_dashboard WHERE deleted_at IS NULL";
    }

    private String dashboardVersionSelect() {
        return "SELECT id,tenant_id,dashboard_id,version_no,layout_json,filters_json,"
                + "version_status,published_at,lock_version"
                + " FROM bi_dashboard_version WHERE deleted_at IS NULL";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private JsonNode tree(String value) {
        return read(value, JsonNode.class);
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private void optimistic(int changed) {
        if (changed != 1) {
            throw new ConflictException("资源已被其他成员更新，或发布版本不可修改");
        }
    }
}
