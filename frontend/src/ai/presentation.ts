import type { ChartSpec } from '../dashboard/types'
import type { QueryResult } from '../query/types'
import type { AiEvidence, AiResultPayload } from './types'

const SUPPORTED_CHARTS = new Set(['KPI', 'BAR', 'LINE', 'PIE', 'TABLE'])

export interface AiChartPresentation { spec: ChartSpec; result: QueryResult }

export function toChartPresentation(payload?: AiResultPayload): AiChartPresentation | undefined {
  if (!payload?.chart_spec || !SUPPORTED_CHARTS.has(payload.chart_spec.type) || !payload.answer.facts.length) return undefined
  const type = payload.chart_spec.type
  const evidence = payload.evidence[0]
  const rows = payload.answer.facts.map(fact => ({ category: fact.statement, value: fact.value }))
  const spec: ChartSpec = {
    type,
    title: payload.chart_spec.title,
    valueFields: ['value'],
    ...(type === 'KPI' ? {} : { categoryField: 'category' }),
    ...(type === 'TABLE' ? { detailColumns: ['category', 'value'] } : {}),
    valueFormat: 'NUMBER',
    legend: false,
    labels: true,
    emptyText: 'AI 查询已完成，但当前范围没有可展示的数据',
  }
  return {
    spec,
    result: {
      queryId: payload.answer.facts[0]?.query_id ?? evidence?.query_id ?? 'ai-result',
      status: 'SUCCEEDED',
      columns: [
        { key: 'category', label: '业务事实', dataType: 'STRING', kind: 'DIMENSION' },
        { key: 'value', label: '数值', dataType: 'DECIMAL', kind: 'METRIC' },
      ],
      rows,
      rowCount: rows.length,
      cached: false,
      publication: {
        generationId: evidence?.publication?.generation_id ?? '',
        committedPublishSeq: evidence?.publication?.committed_publish_seq ?? 0,
        qualityVersion: evidence?.publication?.quality_version ?? 0,
      },
      executionMs: 0,
    },
  }
}

export function taskFailureMessage(code: string, fallback = '智能分析失败'): string {
  const messages: Record<string, string> = {
    AI_TASK_TIMEOUT: '分析任务超时，请缩小数据范围后重试',
    MODEL_UNAVAILABLE: 'AI 模型当前不可用，真实数据查询未被伪造，请稍后重试',
    CANCELLED: '任务已取消，未完成的结果不会作为正式答案展示',
    FORBIDDEN: '当前权限不足或已被撤销，请刷新权限后重新发起分析',
    PERMISSION_REVOKED: '任务执行期间权限已撤销，系统已阻止继续读取数据',
    NO_AUTHORIZED_DATA: '当前权限范围内没有可用于回答的数据',
  }
  return messages[code] ?? fallback
}

export function evidenceSummary(evidence: AiEvidence): Array<{ label: string; value: string }> {
  return [
    { label: '查询 ID', value: evidence.query_id ?? '—' },
    { label: '追踪 ID', value: evidence.trace_id ?? '—' },
    { label: '数据集版本', value: evidence.dataset_version_id ? String(evidence.dataset_version_id) : '—' },
    { label: '指标版本', value: evidence.metric_version_ids?.join(', ') || '—' },
    { label: '查询时间', value: evidence.time_range?.start || evidence.time_range?.end ? `${evidence.time_range.start ?? '未限定'} — ${evidence.time_range.end ?? '未限定'}` : '未限定' },
    { label: '数据发布代', value: evidence.publication?.generation_id !== undefined ? String(evidence.publication.generation_id) : '—' },
    { label: '发布序号', value: evidence.publication?.committed_publish_seq !== undefined ? String(evidence.publication.committed_publish_seq) : '—' },
    { label: '质量版本', value: evidence.publication?.quality_version !== undefined ? String(evidence.publication.quality_version) : '—' },
  ]
}
