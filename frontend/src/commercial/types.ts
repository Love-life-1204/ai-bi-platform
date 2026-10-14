import type { Id } from '../api/resource-id'
export interface Report { id: Id; tenantId: Id; workspaceId: Id; ownerMemberId: Id; name: string; columns: string[]; rows: string[][]; status: string; createdAt: string }
export interface ExportTask { id: Id; tenantId: Id; requesterMemberId: Id; reportId: Id; format: 'CSV' | 'XLSX' | 'PDF'; permissionVersion: number; status: string; errorCode?: string; fileName?: string; contentType?: string; expiresAt?: string; createdAt: string }
export interface ReportSubscription { id: Id; tenantId: Id; reportId: Id; creatorMemberId: Id; schedule: string; timezone: string; recipientMemberIds: Id[]; status: string; nextRunAt: string; createdAt: string }
export interface AlertRule { id: Id; tenantId: Id; workspaceId: Id; name: string; operator: string; threshold: number; ownerMemberId: Id; recipients: Id[]; status: string; suppressionSeconds: number; createdAt: string }
export interface AlertEvent { id: Id; tenantId: Id; ruleId: Id; dedupeKey: string; actualValue: number; evidence: Record<string, unknown>; status: string; occurredAt: string; acknowledgedAt?: string }
export interface PlanVersion { id: Id; planCode: string; planName: string; version: number; priceMinorUnits: number; currency: string; billingPeriod: string; quotas: Record<string, number>; status: string; effectiveFrom: string }
export interface Subscription { id: Id; tenantId: Id; planVersionId: Id; status: string; periodStart: string; periodEnd: string; cancelAtPeriodEnd: boolean }
export interface Order { id: Id; tenantId: Id; subscriptionId: Id; orderNo: string; planVersionId: Id; amountMinorUnits: number; currency: string; status: string; paidAt?: string; createdAt: string; idempotencyKey: string }
export interface QuotaBalance { tenantId: Id; quotaCode: string; limit: number; used: number; reserved: number; remaining: number; windowStart: string; windowEnd: string }
export interface UsageSummary { tenantId: Id; promptTokens: number; completionTokens: number; amountMinorUnits: number; currency: string; entryCount: number; reconciliationDelta: number }
export interface MfaEnrollment { secret: string; otpauthUri: string }
export interface OidcConfig { issuer: string; clientId: string; clientSecretRef: string; authorizationEndpoint: string; tokenEndpoint: string; jwksUri: string; emailDomain?: string; enabled: boolean }
export interface ModelConfig { tenantId: Id; providerCode: string; modelName: string; region: string; dataPolicy: string; externalEnabled: boolean; prices: Record<string, number> }
export interface PlatformOverview { activeTenants: number; activeSubscriptions: number; paidOrders: number; openAlerts: number; usageCostMinorUnits: number; activeSupportGrants: number }
export interface SystemStatus { serviceName: string; status: string; checkedAt: string }

