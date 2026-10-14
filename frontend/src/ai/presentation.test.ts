import { describe, expect, it } from 'vitest'
import { evidenceSummary, taskFailureMessage, toChartPresentation } from './presentation'
import type { AiResultPayload } from './types'

const payload: AiResultPayload = {
  answer: {
    summary: '净销售额为 250 元',
    facts: [{ statement: '净销售额', value: '250.00', unit: 'CNY', metric_version_id: '20', query_id: 'qry-1' }],
    assumptions: [], recommendations: [], limitations: [],
  },
  chart_spec: { type: 'KPI', value_fields: ['net_sales'], title: '核心指标' },
  evidence: [{ query_id: 'qry-1', trace_id: 'trace-1', dataset_version_id: '11', metric_version_ids: ['20'], time_range: { start: '2026-10-01', end: '2026-10-31' }, publication: { generation_id: '1', committed_publish_seq: 37, quality_version: 2 } }],
}

describe('AI result presentation', () => {
  it('maps an allowlisted ChartSpec without executing model options', () => {
    const presentation = toChartPresentation(payload)
    expect(presentation?.spec).toMatchObject({ type: 'KPI', valueFields: ['value'] })
    expect(presentation?.result.rows[0]?.value).toBe('250.00')
  })

  it('does not invent a chart for empty data', () => {
    expect(toChartPresentation({ ...payload, answer: { ...payload.answer, facts: [] } })).toBeUndefined()
  })

  it('explains timeout, unavailable model, cancellation and revoked permission', () => {
    expect(taskFailureMessage('AI_TASK_TIMEOUT')).toContain('超时')
    expect(taskFailureMessage('MODEL_UNAVAILABLE')).toContain('不可用')
    expect(taskFailureMessage('CANCELLED')).toContain('取消')
    expect(taskFailureMessage('PERMISSION_REVOKED')).toContain('权限已撤销')
  })

  it('renders evidence versions and trace identifiers', () => {
    const rows = evidenceSummary(payload.evidence[0]!)
    expect(rows).toContainEqual({ label: '追踪 ID', value: 'trace-1' })
    expect(rows).toContainEqual({ label: '指标版本', value: '20' })
    expect(rows).toContainEqual({ label: '查询时间', value: '2026-10-01 — 2026-10-31' })
  })
})
