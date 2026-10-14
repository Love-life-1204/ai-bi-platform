package com.aibi.platform.dashboard.mapper;

import com.aibi.platform.dashboard.model.DashboardModels.Chart;
import com.aibi.platform.dashboard.model.DashboardModels.ChartVersion;
import com.aibi.platform.dashboard.model.DashboardModels.Dashboard;
import com.aibi.platform.dashboard.model.DashboardModels.DashboardVersion;
import java.util.List;
import java.util.Optional;

public interface DashboardMapper {

    Chart insertChart(Chart chart);

    Chart updateChart(Chart chart, long expectedLockVersion);

    Optional<Chart> findChart(long tenantId, long chartId);

    List<Chart> listCharts(long tenantId, Long workspaceId);

    ChartVersion insertChartVersion(ChartVersion version);

    ChartVersion updateChartVersion(ChartVersion version, long expectedLockVersion);

    Optional<ChartVersion> findChartVersion(long tenantId, long versionId);

    List<ChartVersion> listChartVersions(long tenantId, long chartId);

    Dashboard insertDashboard(Dashboard dashboard);

    Dashboard updateDashboard(Dashboard dashboard, long expectedLockVersion);

    Optional<Dashboard> findDashboard(long tenantId, long dashboardId);

    List<Dashboard> listDashboards(long tenantId, Long workspaceId);

    DashboardVersion insertDashboardVersion(DashboardVersion version);

    DashboardVersion updateDashboardVersion(DashboardVersion version, long expectedLockVersion);

    Optional<DashboardVersion> findDashboardVersion(long tenantId, long versionId);

    List<DashboardVersion> listDashboardVersions(long tenantId, long dashboardId);
}
