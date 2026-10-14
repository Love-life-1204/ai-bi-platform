import type { Id } from '../api/resource-id'
import type { ApiClient } from '../api/client'
import type { AlertEvent, AlertRule, ExportTask, MfaEnrollment, ModelConfig, OidcConfig, Order, PlanVersion, PlatformOverview, QuotaBalance, Report, ReportSubscription, Subscription, SystemStatus, UsageSummary } from './types'

export class CommercialApi {
  constructor(private readonly client: ApiClient) {}
  reports(): Promise<Report[]> { return this.client.request('reports') }
  report(id: Id): Promise<Report> { return this.client.request(`reports/${id}`) }
  createExport(reportId: Id, format: ExportTask['format']): Promise<ExportTask> { return this.client.request('exports', { method: 'POST', body: { reportId, format }, idempotencyKey: crypto.randomUUID() }) }
  exportTask(id: Id): Promise<ExportTask> { return this.client.request(`exports/${id}`, { silent: true }) }
  cancelExport(id: Id): Promise<ExportTask> { return this.client.request(`exports/${id}/cancel`, { method: 'POST' }) }
  downloadExport(id: Id) { return this.client.download(`exports/${id}/download`, { timeoutMs: 30_000 }) }
  subscriptions(): Promise<ReportSubscription[]> { return this.client.request('report-subscriptions') }
  createSubscription(input: { reportId: Id; schedule: string; timezone: string; recipientMemberIds: Id[]; nextRunAt?: string }): Promise<ReportSubscription> { return this.client.request('report-subscriptions', { method: 'POST', body: input }) }
  updateSubscription(id: Id, input: Partial<Pick<ReportSubscription, 'schedule' | 'timezone' | 'recipientMemberIds' | 'status' | 'nextRunAt'>>): Promise<ReportSubscription> { return this.client.request(`report-subscriptions/${id}`, { method: 'PATCH', body: input }) }
  alertRules(): Promise<AlertRule[]> { return this.client.request('alert-rules') }
  createAlertRule(input: { workspaceId: Id; name: string; operator: string; threshold: number; recipients: Id[]; suppressionSeconds: number }): Promise<AlertRule> { return this.client.request('alert-rules', { method: 'POST', body: input }) }
  updateAlertRule(id: Id, input: Partial<Pick<AlertRule, 'name' | 'operator' | 'threshold' | 'recipients' | 'status' | 'suppressionSeconds'>>): Promise<AlertRule> { return this.client.request(`alert-rules/${id}`, { method: 'PATCH', body: input }) }
  alertEvents(): Promise<AlertEvent[]> { return this.client.request('alert-events') }
  acknowledgeAlert(id: Id): Promise<AlertEvent> { return this.client.request(`alert-events/${id}/ack`, { method: 'POST' }) }
  plans(): Promise<PlanVersion[]> { return this.client.request('plans', { auth: 'none' }) }
  subscription(): Promise<Subscription> { return this.client.request('subscriptions/current', { silent: true }) }
  orders(): Promise<Order[]> { return this.client.request('orders') }
  order(id: Id): Promise<Order> { return this.client.request(`orders/${id}`, { silent: true }) }
  createOrder(planVersionId: Id, requestKey: string = crypto.randomUUID()): Promise<Order> { return this.client.request('orders', { method: 'POST', body: { planVersionId }, idempotencyKey: requestKey }) }
  quotas(): Promise<QuotaBalance[]> { return this.client.request('usage/quotas') }
  usage(): Promise<UsageSummary> { return this.client.request('usage/ai') }
  enrollMfa(): Promise<MfaEnrollment> { return this.client.request('auth/mfa/enroll', { method: 'POST', auth: 'global' }) }
  confirmMfa(code: string): Promise<void> { return this.client.request('auth/mfa/confirm', { method: 'POST', body: { code }, auth: 'global' }) }
  disableMfa(code: string): Promise<void> { return this.client.request('auth/mfa', { method: 'DELETE', body: { code }, auth: 'global' }) }
  oidc(): Promise<OidcConfig> { return this.client.request('enterprise/oidc', { silent: true }) }
  configureOidc(input: OidcConfig): Promise<OidcConfig> { return this.client.request('enterprise/oidc', { method: 'PUT', body: input }) }
  configureModel(input: Omit<ModelConfig, 'tenantId'>): Promise<ModelConfig> { return this.client.request('ai/model-config', { method: 'PUT', body: input }) }
  platformOverview(token: string): Promise<PlatformOverview> { return this.client.request('platform/overview', { auth: 'none', headers: { 'X-Platform-Token': token }, silent: true }) }
  systemStatus(): Promise<SystemStatus> { return this.client.request('system/status', { auth: 'none', silent: true }) }
}

