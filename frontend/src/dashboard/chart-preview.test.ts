import { describe, expect, it } from 'vitest'
import { boundedPreviewRows, buildPivotMatrix, numericValue, previewState } from './chart-preview'
import type { QueryResult } from '../query/types'

describe('chart preview edge cases', () => {
  it('distinguishes an empty result', () => {
    expect(previewState(queryResult([]))).toBe('EMPTY')
  })

  it('preserves zero instead of treating it as missing', () => {
    expect(numericValue(0)).toBe(0)
    expect(numericValue('0')).toBe(0)
  })

  it('turns anomalous numeric values into gaps instead of false zeros', () => {
    expect(numericValue('not-a-number')).toBeNull()
    expect(numericValue(Number.POSITIVE_INFINITY)).toBeNull()
  })

  it('bounds a large visual result while retaining coverage', () => {
    const values = Array.from({ length: 5_000 }, (_, index) => index)
    const bounded = boundedPreviewRows(values)
    expect(bounded.truncated).toBe(true)
    expect(bounded.rows).toHaveLength(1_000)
    expect(bounded.rows[0]).toBe(0)
    expect(bounded.rows.at(-1)).toBeGreaterThan(4_900)
  })

  it('builds a pivot matrix and isolates request failure state', () => {
    expect(buildPivotMatrix([
      { region: '华东', month: '一月', sales: 10 },
      { region: '华东', month: '二月', sales: 20 },
    ], 'region', 'month', 'sales')).toEqual({ columns: ['一月', '二月'], rows: [{ key: '华东', values: { 一月: 10, 二月: 20 } }] })
    expect(previewState(undefined, '查询失败')).toBe('ERROR')
  })
})

function queryResult(rows: Record<string, unknown>[]): QueryResult {
  return {
    queryId: 'preview-test',
    status: 'SUCCEEDED',
    columns: [],
    rows,
    rowCount: rows.length,
    cached: false,
    publication: { generationId: '1', committedPublishSeq: 1, qualityVersion: 1 },
    executionMs: 5,
  }
}
