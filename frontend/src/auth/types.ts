import type { Id } from '../api/resource-id'
export interface AuthUser {
  id: Id
  email: string
  displayName: string
  userStatus: string
  createdAt: string
}

export interface LoginData {
  tokenType: string
  accessToken: string
  accessTokenExpiresAt: string
  refreshToken: string
  refreshTokenExpiresAt: string
  user: AuthUser
}

export interface MemberContext {
  id: Id
  tenantId: Id
  userId: Id
  memberName: string
  memberStatus: string
  owner: boolean
  permissionVersion: number
  joinedAt: string
  lockVersion: number
}

export interface TenantContext {
  tokenType: string
  tenantAccessToken: string
  expiresAt: string
  tenantId: Id
  memberId: Id
  tenantPermissionVersion: number
  memberPermissionVersion: number
}

export interface TenantData {
  id: Id
  tenantCode: string
  tenantName: string
  tenantStatus: string
  timezone: string
  defaultCurrency: string
  permissionVersion: number
  createdAt: string
  updatedAt: string
  lockVersion: number
}

export interface WorkspaceData {
  id: Id
  tenantId: Id
  workspaceCode: string
  workspaceName: string
  workspaceStatus: string
  ownerMemberId: Id
  lockVersion: number
}
