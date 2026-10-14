import type { Id } from '../api/resource-id'
export interface QueryFilter {
  fieldId: Id
  operator: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'IN' | 'NOT_IN' | 'IS_NULL' | 'IS_NOT_NULL'
  value?: unknown
}

export interface QueryPlan {
  datasetVersionId: Id
  metricVersionIds: Id[]
  dimensionFieldIds: Id[]
  timeRange?: { fieldId: Id; start: string; end: string }
  filters: QueryFilter[]
  sort: { targetType: 'FIELD' | 'METRIC'; targetId: Id; direction: 'ASC' | 'DESC' }[]
  limit: number
}

export interface QueryColumn {
  key: string
  label: string
  dataType: string
  kind: 'DIMENSION' | 'METRIC'
}

export interface QueryResult {
  queryId: string
  status: string
  columns: QueryColumn[]
  rows: Record<string, unknown>[]
  rowCount: number
  cached: boolean
  publication: { generationId: Id; committedPublishSeq: number; qualityVersion: number }
  executionMs: number
}
