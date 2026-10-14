import type { QueryResult } from '../query/types'

export const MAX_VISUAL_ROWS = 1_000

export interface PivotMatrix {
  columns: string[]
  rows: Array<{ key: string; values: Record<string, unknown> }>
}

export function numericValue(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export function boundedPreviewRows<T>(rows: T[], limit = MAX_VISUAL_ROWS): { rows: T[]; truncated: boolean } {
  if (rows.length <= limit) return { rows, truncated: false }
  const step = rows.length / limit
  return { rows: Array.from({ length: limit }, (_, index) => rows[Math.floor(index * step)]!), truncated: true }
}

export function buildPivotMatrix(rows: Record<string, unknown>[], rowField: string,
    columnField: string, valueField: string): PivotMatrix {
  const columns = [...new Set(rows.map(row => String(row[columnField] ?? '未分类')))]
  const values = new Map<string, Record<string, unknown>>()
  for (const row of rows) {
    const rowKey = String(row[rowField] ?? '未分类')
    const columnKey = String(row[columnField] ?? '未分类')
    const target = values.get(rowKey) ?? {}
    target[columnKey] = row[valueField]
    values.set(rowKey, target)
  }
  return { columns, rows: [...values].map(([key, rowValues]) => ({ key, values: rowValues })) }
}

export function previewState(result: QueryResult | undefined, error = ''): 'LOADING' | 'ERROR' | 'EMPTY' | 'READY' {
  if (error) return 'ERROR'
  if (!result) return 'LOADING'
  return result.rows.length ? 'READY' : 'EMPTY'
}
