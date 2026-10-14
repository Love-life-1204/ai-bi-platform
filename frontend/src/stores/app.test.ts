// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { ApiClientError } from '../api/client'
import { apiClient, authApi } from '../api/runtime'
import { createAppRouter } from '../router'
import { useAppStore } from './app'
import type { LoginData, MemberContext, TenantContext } from '../auth/types'

const future = () => new Date(Date.now() + 60_000).toISOString()
const loginData = (): LoginData => ({
  tokenType: 'Bearer', accessToken: 'global-token', accessTokenExpiresAt: future(),
  refreshToken: 'refresh-token', refreshTokenExpiresAt: future(),
  user: { id: '7', email: 'analyst@example.com', displayName: '分析师', userStatus: 'ACTIVE', createdAt: future() },
})
const member: MemberContext = {
  id: '22', tenantId: '11', userId: '7', memberName: '数据分析师', memberStatus: 'ACTIVE',
  owner: false, permissionVersion: 1, joinedAt: future(), lockVersion: 0,
}
const tenantContext: TenantContext = {
  tokenType: 'Bearer', tenantAccessToken: 'tenant-token', expiresAt: future(), tenantId: '11',
  memberId: '22', tenantPermissionVersion: 1, memberPermissionVersion: 1,
}

describe('登录状态与路由保护', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    window.scrollTo = vi.fn()
    setActivePinia(createPinia())
  })

  afterEach(() => vi.restoreAllMocks())

  it('登录成功后建立当前成员、企业和工作空间上下文', async () => {
    vi.spyOn(authApi, 'login').mockResolvedValue(loginData())
    vi.spyOn(authApi, 'memberships').mockResolvedValue([member])
    vi.spyOn(authApi, 'tenantSummaries').mockResolvedValue([{
      id: '11', tenantCode: 'acme', tenantName: '示例企业', tenantStatus: 'ACTIVE',
    }])
    vi.spyOn(authApi, 'switchTenant').mockResolvedValue(tenantContext)
    vi.spyOn(authApi, 'workspaces').mockResolvedValue([{
      id: '33', tenantId: '11', workspaceCode: 'main', workspaceName: '默认空间',
      workspaceStatus: 'ACTIVE', ownerMemberId: '22', lockVersion: 0,
    }])
    const store = useAppStore()

    await store.login({ email: 'analyst@example.com', password: 'correct-password', tenantId: '11' })

    expect(store.isAuthenticated).toBe(true)
    expect(store.currentMember?.id).toBe('22')
    expect(store.tenantId).toBe('11')
    expect(store.workspaceId).toBe('33')
    expect(localStorage.getItem('aibi.tenantAccessToken')).toBe('tenant-token')
  })

  it('密码错误时不保留任何前端会话', async () => {
    vi.spyOn(authApi, 'login').mockRejectedValue(new ApiClientError({
      code: 'UNAUTHORIZED', message: '请先登录或重新登录', status: 401,
    }))
    const store = useAppStore()

    await expect(store.login({ email: 'analyst@example.com', password: 'wrong-password' }))
      .rejects.toMatchObject({ status: 401 })
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem('aibi.accessToken')).toBeNull()
  })

  it('刷新令牌失效时清理过期身份', async () => {
    localStorage.setItem('aibi.refreshToken', 'expired-session-refresh')
    localStorage.setItem('aibi.refreshTokenExpiresAt', future())
    localStorage.setItem('aibi.tenantId', '11')
    localStorage.setItem('aibi.memberships', JSON.stringify([member]))
    setActivePinia(createPinia())
    vi.spyOn(authApi, 'refresh').mockRejectedValue(new ApiClientError({
      code: 'UNAUTHORIZED', message: '会话已过期', status: 401,
    }))
    const store = useAppStore()

    await expect(store.refreshSession()).resolves.toBe(false)
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem('aibi.refreshToken')).toBeNull()
  })

  it('未登录访问受保护路由时跳转登录并保留返回地址', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/dashboards')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/dashboards')
  })

  it('切换工作空间时清理请求、业务缓存与 AI 上下文并重建页面', () => {
    localStorage.setItem('aibi.workspaceId', '33')
    localStorage.setItem('aibi.workspaces', JSON.stringify([
      { id: '33', tenantId: '11', workspaceCode: 'main', workspaceName: '默认空间', workspaceStatus: 'ACTIVE', ownerMemberId: '22', lockVersion: 0 },
      { id: '44', tenantId: '11', workspaceCode: 'sales', workspaceName: '销售空间', workspaceStatus: 'ACTIVE', ownerMemberId: '22', lockVersion: 0 },
    ]))
    localStorage.setItem('aibi.cache.query.33', 'stale')
    localStorage.setItem('aibi.recentDashboards.11.33', '[8]')
    sessionStorage.setItem('aibi.ai.lastEvent.conversation-1', 'event-9')
    setActivePinia(createPinia())
    const abort = vi.spyOn(apiClient, 'abortAll')
    const store = useAppStore()

    store.switchWorkspace('44')

    expect(store.workspaceId).toBe('44')
    expect(store.contextVersion).toBe(1)
    expect(abort).toHaveBeenCalledOnce()
    expect(localStorage.getItem('aibi.cache.query.33')).toBeNull()
    expect(localStorage.getItem('aibi.recentDashboards.11.33')).toBeNull()
    expect(sessionStorage.getItem('aibi.ai.lastEvent.conversation-1')).toBeNull()
  })

  it('拒绝切换到未授权工作空间并保留原上下文', () => {
    localStorage.setItem('aibi.workspaceId', '33')
    localStorage.setItem('aibi.workspaces', JSON.stringify([
      { id: '33', tenantId: '11', workspaceCode: 'main', workspaceName: '默认空间', workspaceStatus: 'ACTIVE', ownerMemberId: '22', lockVersion: 0 },
    ]))
    setActivePinia(createPinia())
    const store = useAppStore()

    expect(() => store.switchWorkspace('999')).toThrow('无权访问')
    expect(store.workspaceId).toBe('33')
    expect(store.contextVersion).toBe(0)
  })

  it('企业切换成功后替换成员与工作空间并清除旧企业上下文', async () => {
    const secondMember: MemberContext = { ...member, id: '23', tenantId: '12', memberName: '乙企业分析师' }
    localStorage.setItem('aibi.tenantId', '11')
    localStorage.setItem('aibi.memberships', JSON.stringify([member, secondMember]))
    localStorage.setItem('aibi.workspaces', JSON.stringify([{ id: '33', tenantId: '11', workspaceCode: 'main', workspaceName: '默认空间', workspaceStatus: 'ACTIVE', ownerMemberId: '22', lockVersion: 0 }]))
    localStorage.setItem('aibi.workspaceId', '33')
    localStorage.setItem('aibi.cache.chart.11', 'stale-chart')
    sessionStorage.setItem('aibi.ai.lastEvent.old-conversation', 'event-3')
    setActivePinia(createPinia())
    vi.spyOn(authApi, 'switchTenant').mockResolvedValue({ ...tenantContext, tenantId: '12', memberId: '23', tenantAccessToken: 'tenant-12-token' })
    vi.spyOn(authApi, 'workspaces').mockResolvedValue([{ id: '55', tenantId: '12', workspaceCode: 'finance', workspaceName: '财务空间', workspaceStatus: 'ACTIVE', ownerMemberId: '23', lockVersion: 0 }])
    const store = useAppStore()

    await store.switchTenant('12')

    expect(store.tenantId).toBe('12')
    expect(store.currentMember?.id).toBe('23')
    expect(store.workspaceId).toBe('55')
    expect(store.contextVersion).toBe(1)
    expect(localStorage.getItem('aibi.cache.chart.11')).toBeNull()
    expect(sessionStorage.getItem('aibi.ai.lastEvent.old-conversation')).toBeNull()
  })
})
