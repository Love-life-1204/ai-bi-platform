import type { Id } from '../api/resource-id'
import type { QueryColumn, QueryFilter, QueryPlan, QueryResult } from '../query/types'

export type ChartType = 'KPI' | 'BAR' | 'LINE' | 'PIE' | 'STACKED_BAR' | 'TABLE' | 'PIVOT_TABLE'

export interface ChartSpec {
  type: ChartType
  title?: string
  categoryField?: string
  interactionFieldId?: Id
  valueFields: string[]
  seriesNameField?: string
  valueFormat?: 'NUMBER' | 'CURRENCY' | 'PERCENT'
  legend?: boolean
  labels?: boolean
  emptyText?: string
  detailColumns?: string[]
  xAxisTitle?: string
  yAxisTitle?: string
  unit?: string
  decimalPlaces?: number
  drill?: { enabled: boolean; target: 'DETAIL'; dimensionField: string }
}

export interface Chart {
  id: Id
  tenantId: Id
  workspaceId: Id
  code: string
  name: string
  currentVersionId?: Id
  status: 'DRAFT' | 'PUBLISHED'
  ownerMemberId: Id
  createdAt: string
  updatedAt: string
  lockVersion: number
}

export interface ChartVersion {
  id: Id
  chartId: Id
  versionNo: number
  datasetVersionId: Id
  queryPlan: QueryPlan
  chartSpec: ChartSpec
  status: 'DRAFT' | 'PUBLISHED'
  publishedAt?: string
  lockVersion: number
}

export interface ChartData { chart: Chart; current?: ChartVersion; draft?: ChartVersion }

export interface GridPosition { x: number; y: number; w: number; h: number }

export interface DashboardWidget {
  id: Id
  chartVersionId: Id
  key: string
  position: GridPosition
}

export interface Dashboard {
  id: Id
  tenantId: Id
  workspaceId: Id
  code: string
  name: string
  ownerMemberId: Id
  currentVersionId?: Id
  status: 'DRAFT' | 'PUBLISHED'
  createdAt: string
  updatedAt: string
  lockVersion: number
}

export interface DashboardVersion {
  id: Id
  dashboardId: Id
  versionNo: number
  layout: { columns: 12; rowHeight?: number }
  filters: GlobalFilterDefinition[]
  widgets: DashboardWidget[]
  status: 'DRAFT' | 'PUBLISHED'
  publishedAt?: string
  lockVersion: number
}

export interface GlobalFilterDefinition {
  key: string
  label: string
  fieldId: Id
  type: 'DATE_RANGE' | 'SELECT' | 'MULTI_SELECT'
}

export interface DashboardData { dashboard: Dashboard; current?: DashboardVersion; draft?: DashboardVersion }

export interface DashboardQueryInput {
  timeRange?: { fieldId: Id; start: string; end: string }
  filters: QueryFilter[]
  interactionFilters: QueryFilter[]
  previewDraft: boolean
}

export interface WidgetResult {
  key: string
  chartVersionId: Id
  status: 'SUCCEEDED' | 'FAILED'
  result?: QueryResult
  error?: { code: string; message: string; retryable: boolean }
  chartSpec?: ChartSpec
}

export interface DashboardQueryData {
  dashboardId: Id
  dashboardVersionId: Id
  mode: 'PREVIEW' | 'PUBLISHED'
  widgets: WidgetResult[]
}

export interface ChartBindingValidation {
  valid: boolean
  errors: string[]
}

export function validateChartBinding(spec: ChartSpec, columns: QueryColumn[]): ChartBindingValidation {
  const byKey = new Map(columns.map(column => [column.key, column]))
  const errors: string[] = []
  if (spec.categoryField) {
    const category = byKey.get(spec.categoryField)
    if (!category) errors.push(`分类字段 ${spec.categoryField} 不在查询结果中`)
    else if (category.kind !== 'DIMENSION') errors.push(`分类字段 ${spec.categoryField} 必须是维度`)
  }
  if (spec.seriesNameField) {
    const series = byKey.get(spec.seriesNameField)
    if (!series) errors.push(`系列字段 ${spec.seriesNameField} 不在查询结果中`)
    else if (series.kind !== 'DIMENSION') errors.push(`系列字段 ${spec.seriesNameField} 必须是维度`)
  }
  for (const field of spec.valueFields) {
    const column = byKey.get(field)
    if (!column) errors.push(`数值字段 ${field} 不在查询结果中`)
    else if (!['INTEGER', 'LONG', 'DECIMAL', 'NUMBER', 'FLOAT', 'DOUBLE'].includes(column.dataType)) {
      errors.push(`数值字段 ${field} 的类型 ${column.dataType} 不可用于聚合图表`)
    }
  }
  for (const field of spec.detailColumns ?? []) {
    if (!byKey.has(field)) errors.push(`明细列 ${field} 不在查询结果中`)
  }
  return { valid: errors.length === 0, errors }
}
