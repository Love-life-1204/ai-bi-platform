import type { TaskStatus } from './types'

const labels: Record<TaskStatus, string> = {
  PENDING: '排队中',
  RUNNING: '执行中',
  SUCCESS: '成功',
  PARTIAL_SUCCESS: '部分成功',
  FAILED: '失败',
  CANCELLED: '已取消',
  TIMEOUT: '超时',
}

export function taskStatusLabel(status: TaskStatus, deadLetter = false): string {
  return deadLetter ? '死信待补偿' : labels[status]
}

export function taskStatusTone(status: TaskStatus): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PARTIAL_SUCCESS' || status === 'PENDING') return 'warning'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'RUNNING') return 'primary'
  return 'info'
}

export function isTaskActive(status: TaskStatus): boolean {
  return status === 'PENDING' || status === 'RUNNING'
}

export function rowProgress(total: number, valid: number, errors: number, status: TaskStatus): number {
  if (status === 'SUCCESS' || status === 'PARTIAL_SUCCESS') return 100
  if (total <= 0) return status === 'RUNNING' ? 10 : 0
  return Math.min(99, Math.round(((valid + errors) / total) * 100))
}
