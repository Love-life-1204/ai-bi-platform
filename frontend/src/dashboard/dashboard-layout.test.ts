import { describe, expect, it } from 'vitest'
import { clampPosition, movePosition, nextPosition, resizePosition, resolveWidgetResult } from './dashboard-layout'
import type { DashboardWidget, WidgetResult } from './types'

describe('dashboard grid layout', () => {
  it('keeps widgets inside the twelve-column grid', () => {
    expect(clampPosition({ x: 11, y: -2, w: 6, h: 1 })).toEqual({ x: 6, y: 0, w: 6, h: 2 })
  })

  it('moves and resizes with safe minimums and maximums', () => {
    expect(movePosition({ x: 0, y: 0, w: 4, h: 4 }, 10, 3)).toEqual({ x: 8, y: 3, w: 4, h: 4 })
    expect(resizePosition({ x: 8, y: 3, w: 4, h: 4 }, 4, -9)).toEqual({ x: 4, y: 3, w: 8, h: 2 })
  })

  it('places a newly added widget below the existing layout', () => {
    const widgets = [
      { position: { x: 0, y: 0, w: 6, h: 4 } },
      { position: { x: 6, y: 2, w: 6, h: 5 } },
    ] as DashboardWidget[]
    expect(nextPosition(widgets)).toEqual({ x: 0, y: 7, w: 6, h: 4 })
  })

  it('keeps a failed widget isolated and preserves successful money values', () => {
    const successful = resolveWidgetResult({
      key: 'sales', chartVersionId: '1', status: 'SUCCEEDED',
      chartSpec: { type: 'KPI', valueFields: ['metric_1'] },
      result: {
        queryId: 'q1', status: 'SUCCEEDED', columns: [], rows: [{ metric_1: '129.50' }],
        rowCount: 1, cached: false,
        publication: { generationId: '1', committedPublishSeq: 2, qualityVersion: 3 }, executionMs: 8,
      },
    })
    const failed = resolveWidgetResult({ key: 'orders', chartVersionId: '2', status: 'FAILED', error: { code: 'QUERY_TIMEOUT', message: '查询超时', retryable: true } })

    expect(successful.result?.rows[0]?.metric_1).toBe('129.50')
    expect(failed).toMatchObject({ error: '查询超时', retryable: true })
    expect(successful.error).toBe('')
  })
})
