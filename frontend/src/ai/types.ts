import type { Id } from '../api/resource-id'
export interface AiConversation {
  id: Id
  workspaceId: Id
  memberId: Id
  title: string
  status: string
  contextVersion: number
  createdAt: string
  updatedAt: string
}

export interface AiMessage {
  id: Id
  conversationId: Id
  sequence: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  taskId?: Id
  createdAt: string
}

export interface AiTaskAccepted {
  requestId: string
  taskId: string
  status: string
  eventsUrl: string
}

export interface AiTask {
  taskId: string
  conversationId: Id
  status: string
  attempt: number
  contextVersion: number
  lastSequence: number
  errorCode?: string
  deadlineAt: string
  createdAt: string
  finishedAt?: string
}

export interface AiFact {
  statement: string
  value?: string | number
  unit?: string
  metric_version_id?: Id
  query_id: string
}

export interface AiAnswer {
  summary: string
  facts: AiFact[]
  assumptions: string[]
  recommendations: string[]
  limitations: string[]
}

export interface AiResultPayload {
  answer: AiAnswer
  chart_spec?: {
    type: 'KPI' | 'BAR' | 'LINE' | 'PIE' | 'TABLE'
    category_field?: string
    value_fields: string[]
    title: string
  }
  evidence: AiEvidence[]
}

export interface AiClarification {
  code: string
  question: string
  context_version: number
  kind?: 'METRIC' | 'TIME' | 'BUSINESS'
  field?: string
  options: Array<{
    metric_version_id?: Id
    name?: string
    label?: string
    value?: string | number
    unit?: string
    description?: string
    payload?: Record<string, string | number | boolean>
  }>
}

export interface AiEvidence {
  query_id?: string
  trace_id?: string
  dataset_version_id?: Id
  metric_version_ids?: Id[]
  time_range?: { start?: string; end?: string; field_id?: Id } | null
  publication?: { generation_id?: Id; committed_publish_seq?: number; quality_version?: number }
  [key: string]: unknown
}

export interface AiTaskEvent {
  id: string
  type: 'accepted' | 'progress' | 'clarification' | 'result' | 'error' | 'done'
  data: Record<string, unknown>
}

