import type { Id } from '../api/resource-id'
import { ApiClient } from '../api/client'
import type { QueryPlan } from '../query/types'
import type {
  Chart, ChartData, ChartSpec, ChartVersion, Dashboard, DashboardData,
  DashboardQueryData, DashboardQueryInput, DashboardVersion, GlobalFilterDefinition, GridPosition,
} from './types'

interface WidgetInput { key: string; chartVersionId: Id; position: GridPosition }

export class DashboardApi {
  constructor(private readonly client: ApiClient) {}

  listCharts(workspaceId: Id): Promise<Chart[]> {
    return this.client.request(`/charts?workspaceId=${workspaceId}`)
  }

  createChart(input: {
    workspaceId: Id; code: string; name: string; datasetVersionId: Id
    queryPlan: QueryPlan; chartSpec: ChartSpec
  }): Promise<ChartData> {
    return this.client.request('/charts', { method: 'POST', body: input })
  }

  getChart(chartId: Id): Promise<ChartData> {
    return this.client.request(`/charts/${chartId}`)
  }

  listChartVersions(chartId: Id): Promise<ChartVersion[]> {
    return this.client.request(`/charts/${chartId}/versions`)
  }

  updateChart(chartId: Id, input: {
    name: string; datasetVersionId: Id; queryPlan: QueryPlan
    chartSpec: ChartSpec; lockVersion: number
  }): Promise<ChartData> {
    return this.client.request(`/charts/${chartId}`, { method: 'PATCH', body: input })
  }

  validateChart(chartId: Id): Promise<{ valid: boolean; errors: unknown[] }> {
    return this.client.request(`/charts/${chartId}/validate`, { method: 'POST' })
  }

  publishChart(chartId: Id): Promise<ChartVersion> {
    return this.client.request(`/charts/${chartId}/publish`, { method: 'POST' })
  }

  listDashboards(workspaceId: Id): Promise<Dashboard[]> {
    return this.client.request(`/dashboards?workspaceId=${workspaceId}`)
  }

  getDashboard(dashboardId: Id): Promise<DashboardData> {
    return this.client.request(`/dashboards/${dashboardId}`)
  }

  listDashboardVersions(dashboardId: Id): Promise<DashboardVersion[]> {
    return this.client.request(`/dashboards/${dashboardId}/versions`)
  }

  createDashboard(input: {
    workspaceId: Id; code: string; name: string; layout: { columns: 12; rowHeight: number }
    filters: GlobalFilterDefinition[]; widgets: WidgetInput[]
  }): Promise<DashboardData> {
    return this.client.request('/dashboards', { method: 'POST', body: input })
  }

  updateDashboard(dashboardId: Id, input: {
    name: string; layout: { columns: 12; rowHeight: number }
    filters: GlobalFilterDefinition[]; widgets: WidgetInput[]; lockVersion: number
  }): Promise<DashboardData> {
    return this.client.request(`/dashboards/${dashboardId}`, { method: 'PATCH', body: input })
  }

  publishDashboard(dashboardId: Id): Promise<DashboardVersion> {
    return this.client.request(`/dashboards/${dashboardId}/publish`, { method: 'POST' })
  }

  queryDashboard(dashboardId: Id, input: DashboardQueryInput, signal?: AbortSignal): Promise<DashboardQueryData> {
    return this.client.request(`/dashboards/${dashboardId}/query`, {
      method: 'POST', body: input, timeoutMs: 35_000, silent: true,
      ...(signal ? { signal } : {}),
    })
  }
}
