import type { Id } from './resource-id'
import type { ApiClient } from './client'
import type { MemberContext, TenantData } from '../auth/types'

export interface DepartmentData {
  id: Id
  tenantId: Id
  parentId?: Id
  departmentCode: string
  departmentName: string
  sortOrder: number
  lockVersion: number
  children: DepartmentData[]
}

export interface RoleData {
  id: Id
  tenantId: Id
  roleCode: string
  roleName: string
  roleStatus: string
  builtin: boolean
  lockVersion: number
}

export interface MemberRoleData {
  id: Id
  tenantId: Id
  memberId: Id
  roleId: Id
  roleCode: string
  workspaceId?: Id
  scopeKey: string
  expiresAt?: string
  effective: boolean
}

export interface InvitationData {
  id: Id
  tenantId: Id
  email: string
  workspaceId?: Id
  roleId?: Id
  invitationStatus: string
  expiresAt: string
  inviteToken?: string
}

export class OrganizationApi {
  constructor(private readonly client: ApiClient) {}

  tenant(tenantId: Id): Promise<TenantData> {
    return this.client.request<TenantData>(`/tenants/${tenantId}`)
  }

  updateTenant(tenantId: Id, input: Pick<TenantData, 'tenantName' | 'timezone' | 'defaultCurrency' | 'lockVersion'>): Promise<TenantData> {
    return this.client.request<TenantData>(`/tenants/${tenantId}`, { method: 'PUT', body: input })
  }

  members(tenantId: Id): Promise<MemberContext[]> {
    return this.client.request<MemberContext[]>(`/tenants/${tenantId}/members`)
  }

  departments(tenantId: Id): Promise<DepartmentData[]> {
    return this.client.request<DepartmentData[]>(`/tenants/${tenantId}/departments/tree`)
  }

  roles(tenantId: Id): Promise<RoleData[]> {
    return this.client.request<RoleData[]>(`/tenants/${tenantId}/roles`)
  }

  memberRoles(tenantId: Id, memberId: Id): Promise<MemberRoleData[]> {
    return this.client.request<MemberRoleData[]>(`/tenants/${tenantId}/members/${memberId}/roles`)
  }

  assignRole(tenantId: Id, memberId: Id, input: {
    roleId: Id; workspaceId?: Id; expiresAt?: string; memberLockVersion: number
  }): Promise<MemberRoleData> {
    return this.client.request<MemberRoleData>(`/tenants/${tenantId}/members/${memberId}/roles`, {
      method: 'POST', body: input,
    })
  }

  invite(tenantId: Id, input: {
    email: string; workspaceId?: Id; roleId?: Id; expiresInHours: number
  }): Promise<InvitationData> {
    return this.client.request<InvitationData>(`/tenants/${tenantId}/invitations`, { method: 'POST', body: input })
  }
}
