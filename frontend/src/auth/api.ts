import type { Id } from '../api/resource-id'
import { normalizeIdFields, resourceId } from '../api/resource-id'
import type { ApiClient } from '../api/client'
import type { LoginData, MemberContext, TenantContext, TenantData, WorkspaceData } from './types'

export class AuthApi {
  constructor(private readonly client: ApiClient) {}

  login(email: string, password: string, deviceFingerprint: string): Promise<LoginData> {
    return this.client.request<LoginData>('/auth/login', {
      method: 'POST', auth: 'none', skipAuthRefresh: true,
      body: { email, password, deviceFingerprint },
    }).then(data => ({ ...data, user: normalizeIdFields(data.user, ['id']) }))
  }

  refresh(refreshToken: string): Promise<LoginData> {
    return this.client.request<LoginData>('/auth/refresh', {
      method: 'POST', auth: 'none', skipAuthRefresh: true, silent: true,
      body: { refreshToken },
    }).then(data => ({ ...data, user: normalizeIdFields(data.user, ['id']) }))
  }

  logout(): Promise<void> {
    return this.client.request<void>('/auth/logout', {
      method: 'POST', auth: 'global', skipAuthRefresh: true, silent: true,
    })
  }

  memberships(): Promise<MemberContext[]> {
    return this.client.request<MemberContext[]>('/users/me/memberships', { auth: 'global' })
      .then(data => data.map(member => normalizeIdFields(member, ['id', 'tenantId', 'userId'])))
  }

  tenantSummaries(): Promise<Pick<TenantData, 'id' | 'tenantCode' | 'tenantName' | 'tenantStatus'>[]> {
    return this.client.request<Pick<TenantData, 'id' | 'tenantCode' | 'tenantName' | 'tenantStatus'>[]>(
      '/users/me/tenants', { auth: 'global' },
    ).then(data => data.map(tenant => normalizeIdFields(tenant, ['id'])))
  }

  switchTenant(tenantId: Id): Promise<TenantContext> {
    return this.client.request<TenantContext>('/auth/switch-tenant', {
      method: 'POST', auth: 'global', body: { tenantId: resourceId(tenantId) },
    }).then(data => normalizeIdFields(data, ['tenantId', 'memberId']))
  }

  tenant(tenantId: Id): Promise<TenantData> {
    return this.client.request<TenantData>(`/tenants/${resourceId(tenantId)}`)
      .then(data => normalizeIdFields(data, ['id']))
  }

  workspaces(tenantId: Id): Promise<WorkspaceData[]> {
    return this.client.request<WorkspaceData[]>(`/tenants/${resourceId(tenantId)}/workspaces`)
      .then(data => data.map(workspace => normalizeIdFields(workspace, ['id', 'tenantId', 'ownerMemberId'])))
  }
}
