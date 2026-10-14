// @vitest-environment jsdom
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { authApi } from '../api/runtime'
import { useAppStore } from './app'
import type { LoginData, MemberContext, TenantContext, WorkspaceData } from '../auth/types'

const exact = '9007199254740993'
const max = '9223372036854775807'
const future = () => new Date(Date.now() + 60_000).toISOString()

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  setActivePinia(createPinia())
})
afterEach(() => vi.restoreAllMocks())

it('upgrades safe legacy identity cache IDs on read', () => {
  localStorage.setItem('aibi.memberships', '[{"id":7,"tenantId":11,"userId":13}]')
  expect(useAppStore().memberships[0]).toMatchObject({ id: '7', tenantId: '11', userId: '13' })
})

it('rejects unsafe cached member context while retaining the global session and unrelated preferences', () => {
  localStorage.setItem('aibi.currentMember', '{"id":9007199254740993,"tenantId":11,"userId":13}')
  localStorage.setItem('aibi.accessToken', 'global-session-to-revalidate')
  localStorage.setItem('aibi.deviceFingerprint', 'saved-device')
  expect(useAppStore().currentMember).toBeNull()
  expect(localStorage.getItem('aibi.currentMember')).toBeNull()
  expect(localStorage.getItem('aibi.accessToken')).toBe('global-session-to-revalidate')
  expect(localStorage.getItem('aibi.deviceFingerprint')).toBe('saved-device')
})

it('restores workspace IDs from their original decimal strings without Number conversion', () => {
  localStorage.setItem('aibi.workspaceId', exact)
  expect(useAppStore().workspaceId).toBe(exact)
})

it('passes exact string IDs through login, tenant switch, workspaces and Pinia', async () => {
  vi.spyOn(authApi, 'login').mockResolvedValue({
    tokenType: 'Bearer', accessToken: 'global-id-test', accessTokenExpiresAt: future(),
    refreshToken: 'refresh-id-test', refreshTokenExpiresAt: future(),
    user: { id: exact, email: 'id@example.test', displayName: 'ID', userStatus: 'ACTIVE', createdAt: future() },
  } as unknown as LoginData)
  vi.spyOn(authApi, 'memberships').mockResolvedValue([{
    id: max, tenantId: exact, userId: exact, memberName: 'ID', memberStatus: 'ACTIVE', owner: true,
    permissionVersion: 1, joinedAt: future(), lockVersion: 0,
  } as unknown as MemberContext])
  vi.spyOn(authApi, 'tenantSummaries').mockResolvedValue([{
    id: exact, tenantCode: 'id', tenantName: 'ID', tenantStatus: 'ACTIVE',
  }] as unknown as Awaited<ReturnType<typeof authApi.tenantSummaries>>)
  const switching = vi.spyOn(authApi, 'switchTenant').mockResolvedValue({
    tokenType: 'Bearer', tenantAccessToken: 'tenant-id-test', expiresAt: future(), tenantId: exact,
    memberId: max, tenantPermissionVersion: 1, memberPermissionVersion: 1,
  } as unknown as TenantContext)
  vi.spyOn(authApi, 'workspaces').mockResolvedValue([{
    id: max, tenantId: exact, workspaceCode: 'id', workspaceName: 'ID', workspaceStatus: 'ACTIVE',
    ownerMemberId: max, lockVersion: 0,
  } as unknown as WorkspaceData])
  const store = useAppStore()
  await store.login({ email: 'id@example.test', password: 'test-only-password', tenantId: exact })
  expect(switching).toHaveBeenCalledWith(exact)
  expect(store.tenantId).toBe(exact)
  expect(store.workspaceId).toBe(max)
  expect(store.currentMember?.id).toBe(max)
  expect(localStorage.getItem('aibi.workspaceId')).toBe(max)
})
