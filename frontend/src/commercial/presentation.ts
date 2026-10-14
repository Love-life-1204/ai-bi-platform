import type { ExportTask, Order, QuotaBalance } from './types'

export function exportStatus(status: string, errorCode?: string): { label: string; type: 'info' | 'warning' | 'success' | 'danger'; terminal: boolean } {
  if (status === 'SUCCEEDED') return { label: '可下载', type: 'success', terminal: true }
  if (status === 'FAILED') return { label: errorCode ? `失败 · ${errorCode}` : '失败', type: 'danger', terminal: true }
  if (status === 'CANCELLED') return { label: '已取消', type: 'info', terminal: true }
  if (status === 'RUNNING') return { label: '生成中', type: 'warning', terminal: false }
  return { label: '排队中', type: 'warning', terminal: false }
}

export function paymentStatus(order: Pick<Order, 'status' | 'paidAt'>): { label: string; confirmed: boolean; type: 'info' | 'warning' | 'success' | 'danger' } {
  if (order.status === 'PAID' && order.paidAt) return { label: '服务端已确认支付', confirmed: true, type: 'success' }
  if (['FAILED', 'CANCELLED', 'REFUNDED'].includes(order.status)) return { label: order.status === 'REFUNDED' ? '支付已退款' : '支付未完成', confirmed: false, type: 'danger' }
  return { label: '等待服务端支付回调', confirmed: false, type: 'warning' }
}

export function quotaPercent(quota: Pick<QuotaBalance, 'limit' | 'used' | 'reserved'>): number {
  if (quota.limit <= 0) return 0
  return Math.min(100, Math.round(((quota.used + quota.reserved) / quota.limit) * 100))
}

export function shouldPollExport(task: ExportTask): boolean { return !exportStatus(task.status, task.errorCode).terminal }

