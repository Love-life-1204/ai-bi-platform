package com.aibi.platform.dashboard.mapper;

import com.aibi.platform.common.exception.ConflictException;
import com.aibi.platform.dashboard.model.DashboardModels.Chart;
import com.aibi.platform.dashboard.model.DashboardModels.ChartVersion;
import com.aibi.platform.dashboard.model.DashboardModels.Dashboard;
import com.aibi.platform.dashboard.model.DashboardModels.DashboardVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryDashboardMapper implements DashboardMapper {

    private final Map<Long, Chart> charts = new ConcurrentHashMap<>();
    private final Map<Long, ChartVersion> chartVersions = new ConcurrentHashMap<>();
    private final Map<Long, Dashboard> dashboards = new ConcurrentHashMap<>();
    private final Map<Long, DashboardVersion> dashboardVersions = new ConcurrentHashMap<>();

    @Override
    public synchronized Chart insertChart(Chart value) {
        if (charts.values().stream().anyMatch(existing -> existing.tenantId() == value.tenantId()
                && existing.workspaceId() == value.workspaceId()
                && existing.code().equals(value.code()))) {
            throw new ConflictException("图表编码已存在");
        }
        charts.put(value.id(), value);
        return value;
    }

    @Override
    public synchronized Chart updateChart(Chart value, long expectedLockVersion) {
        Chart current = charts.get(value.id());
        optimistic(current != null && current.tenantId() == value.tenantId()
                && current.lockVersion() == expectedLockVersion);
        charts.put(value.id(), value);
        return value;
    }

    @Override
    public Optional<Chart> findChart(long tenantId, long chartId) {
        return Optional.ofNullable(charts.get(chartId)).filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public List<Chart> listCharts(long tenantId, Long workspaceId) {
        return charts.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> workspaceId == null || value.workspaceId() == workspaceId)
                .sorted(Comparator.comparing(Chart::code)).toList();
    }

    @Override
    public synchronized ChartVersion insertChartVersion(ChartVersion value) {
        boolean duplicate = chartVersions.values().stream().anyMatch(existing ->
                existing.tenantId() == value.tenantId() && existing.chartId() == value.chartId()
                        && existing.versionNo() == value.versionNo());
        if (duplicate) {
            throw new ConflictException("图表版本号已存在");
        }
        chartVersions.put(value.id(), value);
        return value;
    }

    @Override
    public synchronized ChartVersion updateChartVersion(ChartVersion value, long expectedLockVersion) {
        ChartVersion current = chartVersions.get(value.id());
        optimistic(current != null && current.tenantId() == value.tenantId()
                && current.lockVersion() == expectedLockVersion);
        if ("PUBLISHED".equals(current.status())) {
            throw new ConflictException("已发布图表版本不可修改");
        }
        chartVersions.put(value.id(), value);
        return value;
    }

    @Override
    public Optional<ChartVersion> findChartVersion(long tenantId, long versionId) {
        return Optional.ofNullable(chartVersions.get(versionId))
                .filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public List<ChartVersion> listChartVersions(long tenantId, long chartId) {
        return chartVersions.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> value.chartId() == chartId)
                .sorted(Comparator.comparingInt(ChartVersion::versionNo)).toList();
    }

    @Override
    public synchronized Dashboard insertDashboard(Dashboard value) {
        if (dashboards.values().stream().anyMatch(existing -> existing.tenantId() == value.tenantId()
                && existing.workspaceId() == value.workspaceId()
                && existing.code().equals(value.code()))) {
            throw new ConflictException("看板编码已存在");
        }
        dashboards.put(value.id(), value);
        return value;
    }

    @Override
    public synchronized Dashboard updateDashboard(Dashboard value, long expectedLockVersion) {
        Dashboard current = dashboards.get(value.id());
        optimistic(current != null && current.tenantId() == value.tenantId()
                && current.lockVersion() == expectedLockVersion);
        dashboards.put(value.id(), value);
        return value;
    }

    @Override
    public Optional<Dashboard> findDashboard(long tenantId, long dashboardId) {
        return Optional.ofNullable(dashboards.get(dashboardId))
                .filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public List<Dashboard> listDashboards(long tenantId, Long workspaceId) {
        return dashboards.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> workspaceId == null || value.workspaceId() == workspaceId)
                .sorted(Comparator.comparing(Dashboard::code)).toList();
    }

    @Override
    public synchronized DashboardVersion insertDashboardVersion(DashboardVersion value) {
        boolean duplicate = dashboardVersions.values().stream().anyMatch(existing ->
                existing.tenantId() == value.tenantId()
                        && existing.dashboardId() == value.dashboardId()
                        && existing.versionNo() == value.versionNo());
        if (duplicate) {
            throw new ConflictException("看板版本号已存在");
        }
        dashboardVersions.put(value.id(), value);
        return value;
    }

    @Override
    public synchronized DashboardVersion updateDashboardVersion(
            DashboardVersion value, long expectedLockVersion) {
        DashboardVersion current = dashboardVersions.get(value.id());
        optimistic(current != null && current.tenantId() == value.tenantId()
                && current.lockVersion() == expectedLockVersion);
        if ("PUBLISHED".equals(current.status())) {
            throw new ConflictException("已发布看板版本不可修改");
        }
        dashboardVersions.put(value.id(), value);
        return value;
    }

    @Override
    public Optional<DashboardVersion> findDashboardVersion(long tenantId, long versionId) {
        return Optional.ofNullable(dashboardVersions.get(versionId))
                .filter(value -> value.tenantId() == tenantId);
    }

    @Override
    public List<DashboardVersion> listDashboardVersions(long tenantId, long dashboardId) {
        return dashboardVersions.values().stream().filter(value -> value.tenantId() == tenantId)
                .filter(value -> value.dashboardId() == dashboardId)
                .sorted(Comparator.comparingInt(DashboardVersion::versionNo)).toList();
    }

    private void optimistic(boolean matches) {
        if (!matches) {
            throw new ConflictException("资源已被其他成员更新，请刷新后重试");
        }
    }
}
