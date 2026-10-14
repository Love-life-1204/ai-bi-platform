import { describe, expect, it } from 'vitest'
import { validateChartBinding, type ChartSpec } from './types'

describe('validateChartBinding', () => {
  const columns = [
    { key: 'dimension_1', label: '区域', dataType: 'STRING', kind: 'DIMENSION' as const },
    { key: 'metric_2', label: '净销售额', dataType: 'DECIMAL', kind: 'METRIC' as const },
  ]

  it.each(['BAR', 'LINE', 'PIE', 'STACKED_BAR'] as const)('accepts %s field bindings', type => {
    const spec: ChartSpec = { type, categoryField: 'dimension_1', valueFields: ['metric_2'] }
    expect(validateChartBinding(spec, columns)).toEqual({ valid: true, errors: [] })
  })

  it('accepts pivot table row, column and value bindings', () => {
    const pivotColumns = [...columns, { key: 'dimension_3', label: '月份', dataType: 'STRING', kind: 'DIMENSION' as const }]
    const spec: ChartSpec = { type: 'PIVOT_TABLE', categoryField: 'dimension_1',
      seriesNameField: 'dimension_3', valueFields: ['metric_2'] }
    expect(validateChartBinding(spec, pivotColumns)).toEqual({ valid: true, errors: [] })
  })

  it('rejects a dimension used as a numeric series', () => {
    const spec: ChartSpec = { type: 'KPI', valueFields: ['dimension_1'] }
    const result = validateChartBinding(spec, columns)
    expect(result.valid).toBe(false)
    expect(result.errors[0]).toContain('不可用于聚合图表')
  })
})
