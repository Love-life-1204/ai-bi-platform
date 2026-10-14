import type { Id } from '../api/resource-id'
import { normalizeIdFields, resourceId } from '../api/resource-id'
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { apiClient, authApi } from '../api/runtime'
import type { AuthUser, LoginData, MemberContext, TenantContext, WorkspaceData } from '../auth/types'

export interface TenantOption {
  id: string
  name: string
  code?: string
}

interface LoginInput {
  email: string
  password: string
  tenantId?: string
}

function readStorage(key: string, fallback: string): string {
  return globalThis.localStorage?.getItem(key) ?? fallback
}

function readJson<T>(key: string, fallback: T): T {
  const value = globalThis.localStorage?.getItem(key)
  if (!value) return fallback
  try {
    const parsed = JSON.parse(value) as unknown
    const fields: Record<string, readonly string[]> = {
      'aibi.user': ['id'],
      'aibi.currentMember': ['id', 'tenantId', 'userId'],
      'aibi.memberships': ['id', 'tenantId', 'userId'],
      'aibi.workspaces': ['id', 'tenantId', 'ownerMemberId'],
    }
    const ids = fields[key]
    if (!ids || parsed === null) return parsed as T
    const normalize = (item: unknown) => normalizeIdFields(item as Record<string, unknown>, ids)
    const result = Array.isArray(parsed) ? parsed.map(normalize) : normalize(parsed)
    localStorage.setItem(key, JSON.stringify(result))
    return result as T
  } catch {
    localStorage.removeItem(key)
    return fallback
  }
}

function writeJson(key: string, value: unknown): void {
  localStorage.setItem(key, JSON.stringify(value))
}

export function clearBusinessContextStorage(): void {
  apiClient.abortAll()
  for (const storage of [globalThis.localStorage, globalThis.sessionStorage]) {
    if (!storage) continue
    const keys = Array.from({ length: storage.length }, (_, index) => storage.key(index))
    keys.filter((key): key is string => Boolean(key) && (
      key!.startsWith('aibi.cache.') || key!.startsWith('aibi.recentDashboards.')
      || key!.startsWith('aibi.ai.lastEvent.') || key!.startsWith('aibi.datasourceSelection.')
    )).forEach(key => storage.removeItem(key))
  }
}

function deviceFingerprint(): string {
  const existing = readStorage('aibi.deviceFingerprint', '')
  if (existing) return existing
  const value = globalThis.crypto.randomUUID()
  localStorage.setItem('aibi.deviceFingerprint', value)
  return value
}

function isFuture(iso: string): boolean {
  const timestamp = Date.parse(iso)
  return Number.isFinite(timestamp) && timestamp > Date.now() + 5_000
}

function readTenants(currentId: string): TenantOption[] {
  const saved = globalThis.localStorage?.getItem('aibi.tenants')
  if (saved) {
    try {
      const parsed = JSON.parse(saved) as unknown
      if (Array.isArray(parsed)) {
        const values = parsed.filter((item): item is TenantOption => (
          typeof item === 'object' && item !== null
          && typeof (item as TenantOption).id === 'string'
          && typeof (item as TenantOption).name === 'string'
        ))
        if (values.length > 0) return values
      }
    } catch {
      // Ignore invalid local bootstrap data; the identity API will replace it after login.
    }
  }
  return currentId ? [{ id: currentId, name: `企业 ${currentId}` }] : []
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(readStorage('aibi.sidebarCollapsed', 'false') === 'true')
  const mobileSidebarOpen = ref(false)
  const accessToken = ref(readStorage('aibi.accessToken', ''))
  const accessTokenExpiresAt = ref(readStorage('aibi.accessTokenExpiresAt', ''))
  const refreshToken = ref(readStorage('aibi.refreshToken', ''))
  const refreshTokenExpiresAt = ref(readStorage('aibi.refreshTokenExpiresAt', ''))
  const tenantAccessToken = ref(readStorage('aibi.tenantAccessToken', ''))
  const tenantAccessTokenExpiresAt = ref(readStorage('aibi.tenantAccessTokenExpiresAt', ''))
  const tenantId = ref(readStorage('aibi.tenantId', ''))
  const workspaceId = ref<Id>(readStorage('aibi.workspaceId', ''))
  const tenants = ref<TenantOption[]>(readTenants(tenantId.value))
  const memberships = ref<MemberContext[]>(readJson('aibi.memberships', []))
  const workspaces = ref<WorkspaceData[]>(readJson('aibi.workspaces', []))
  const user = ref<AuthUser | null>(readJson<AuthUser | null>('aibi.user', null))
  const currentMember = ref<MemberContext | null>(readJson<MemberContext | null>('aibi.currentMember', null))
  const notificationCount = ref(0)
  const switchingTenant = ref(false)
  const contextVersion = ref(0)
  let refreshInFlight: Promise<boolean> | null = null

  const isAuthenticated = computed(() => Boolean(
    accessToken.value && tenantAccessToken.value && currentMember.value && tenantId.value,
  ))
  const userName = computed(() => user.value?.displayName || currentMember.value?.memberName || '用户')
  const userEmail = computed(() => user.value?.email ?? '')
  const userInitial = computed(() => userName.value.trim().slice(0, 1).toUpperCase() || 'A')

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('aibi.sidebarCollapsed', String(sidebarCollapsed.value))
  }

  function setMobileSidebar(open: boolean): void {
    mobileSidebarOpen.value = open
  }

  function saveLogin(data: LoginData): void {
    localStorage.setItem('aibi.idSchemaVersion', '2')
    accessToken.value = data.accessToken
    accessTokenExpiresAt.value = data.accessTokenExpiresAt
    refreshToken.value = data.refreshToken
    refreshTokenExpiresAt.value = data.refreshTokenExpiresAt
    user.value = data.user
    localStorage.setItem('aibi.accessToken', data.accessToken)
    localStorage.setItem('aibi.accessTokenExpiresAt', data.accessTokenExpiresAt)
    localStorage.setItem('aibi.refreshToken', data.refreshToken)
    localStorage.setItem('aibi.refreshTokenExpiresAt', data.refreshTokenExpiresAt)
    writeJson('aibi.user', data.user)
  }

  function saveTenantContext(context: TenantContext, member: MemberContext): void {
    tenantAccessToken.value = context.tenantAccessToken
    tenantAccessTokenExpiresAt.value = context.expiresAt
    tenantId.value = String(context.tenantId)
    currentMember.value = member
    localStorage.setItem('aibi.tenantAccessToken', context.tenantAccessToken)
    localStorage.setItem('aibi.tenantAccessTokenExpiresAt', context.expiresAt)
    localStorage.setItem('aibi.tenantId', String(context.tenantId))
    localStorage.setItem('aibi.memberId', String(context.memberId))
    writeJson('aibi.currentMember', member)
  }

  async function establishTenant(id: string): Promise<void> {
    const member = memberships.value.find(item => String(item.tenantId) === id && item.memberStatus === 'ACTIVE')
    if (!member) throw new Error('当前账号不是该企业的有效成员')
    const context = await authApi.switchTenant(resourceId(id))
    saveTenantContext(context, member)
    workspaces.value = (await authApi.workspaces(context.tenantId))
      .filter(workspace => workspace.workspaceStatus === 'ACTIVE')
    workspaceId.value = workspaces.value[0]?.id ?? ''
    localStorage.setItem('aibi.workspaceId', String(workspaceId.value))
    writeJson('aibi.workspaces', workspaces.value)
  }

  async function loadTenants(activeMemberships: MemberContext[]): Promise<void> {
    const summaries = await authApi.tenantSummaries()
    tenants.value = activeMemberships.map((member) => {
      const summary = summaries.find(item => String(item.id) === String(member.tenantId))
      return summary
        ? { id: String(member.tenantId), name: summary.tenantName, code: summary.tenantCode }
        : { id: String(member.tenantId), name: member.memberName }
    })
    writeJson('aibi.tenants', tenants.value)
  }

  async function login(input: LoginInput): Promise<void> {
    const requestedTenant = input.tenantId?.trim()
    const preferredTenant = requestedTenant || tenantId.value
    clearSession()
    try {
      const data = await authApi.login(input.email.trim(), input.password, deviceFingerprint())
      saveLogin(data)
      memberships.value = (await authApi.memberships()).filter(item => item.memberStatus === 'ACTIVE')
      if (memberships.value.length === 0) throw new Error('当前账号尚未加入可用企业，请联系管理员')
      writeJson('aibi.memberships', memberships.value)
      await loadTenants(memberships.value)
      if (requestedTenant && !memberships.value.some(item => String(item.tenantId) === requestedTenant)) {
        throw new Error('当前账号不是所选企业的有效成员')
      }
      const selected = memberships.value.some(item => String(item.tenantId) === preferredTenant)
        ? preferredTenant : String(memberships.value[0]!.tenantId)
      await establishTenant(selected)
    } catch (error) {
      clearSession()
      throw error
    }
  }

  function restoreTenantContext(snapshot: {
    tenantId: string
    tenantAccessToken: string
    tenantAccessTokenExpiresAt: string
    currentMember: MemberContext | null
    workspaces: WorkspaceData[]
    workspaceId: Id
  }): void {
    tenantId.value = snapshot.tenantId
    tenantAccessToken.value = snapshot.tenantAccessToken
    tenantAccessTokenExpiresAt.value = snapshot.tenantAccessTokenExpiresAt
    currentMember.value = snapshot.currentMember
    workspaces.value = snapshot.workspaces
    workspaceId.value = snapshot.workspaceId
    const values: Record<string, string> = {
      'aibi.tenantId': snapshot.tenantId,
      'aibi.tenantAccessToken': snapshot.tenantAccessToken,
      'aibi.tenantAccessTokenExpiresAt': snapshot.tenantAccessTokenExpiresAt,
      'aibi.memberId': snapshot.currentMember ? String(snapshot.currentMember.id) : '',
      'aibi.workspaceId': String(snapshot.workspaceId),
    }
    Object.entries(values).forEach(([key, value]) => value
      ? localStorage.setItem(key, value) : localStorage.removeItem(key))
    if (snapshot.currentMember) writeJson('aibi.currentMember', snapshot.currentMember)
    else localStorage.removeItem('aibi.currentMember')
    writeJson('aibi.workspaces', snapshot.workspaces)
  }

  async function switchTenant(id: string): Promise<void> {
    if (id === tenantId.value || switchingTenant.value) return
    const snapshot = {
      tenantId: tenantId.value,
      tenantAccessToken: tenantAccessToken.value,
      tenantAccessTokenExpiresAt: tenantAccessTokenExpiresAt.value,
      currentMember: currentMember.value,
      workspaces: [...workspaces.value],
      workspaceId: workspaceId.value,
    }
    switchingTenant.value = true
    try {
      await establishTenant(id)
      clearBusinessContextStorage()
      contextVersion.value += 1
    } catch (error) {
      restoreTenantContext(snapshot)
      throw error
    } finally {
      switchingTenant.value = false
    }
  }

  function switchWorkspace(id: Id): void {
    if (id === workspaceId.value) return
    if (!workspaces.value.some(workspace => workspace.id === id && workspace.workspaceStatus === 'ACTIVE')) {
      throw new Error('当前成员无权访问该工作空间')
    }
    clearBusinessContextStorage()
    workspaceId.value = id
    localStorage.setItem('aibi.workspaceId', String(id))
    contextVersion.value += 1
  }

  async function performRefresh(): Promise<boolean> {
    if (!refreshToken.value || !isFuture(refreshTokenExpiresAt.value)) {
      clearSession()
      return false
    }
    const selectedTenant = tenantId.value
    try {
      const data = await authApi.refresh(refreshToken.value)
      saveLogin(data)
      if (memberships.value.length === 0) {
        memberships.value = (await authApi.memberships()).filter(item => item.memberStatus === 'ACTIVE')
        writeJson('aibi.memberships', memberships.value)
      }
      const selected = memberships.value.some(member => member.tenantId === selectedTenant)
        ? selectedTenant : memberships.value[0]?.tenantId
      if (!selected) throw new Error('当前账号没有可用企业成员身份')
      await establishTenant(selected)
      return true
    } catch {
      clearSession()
      return false
    }
  }

  function refreshSession(): Promise<boolean> {
    if (!refreshInFlight) refreshInFlight = performRefresh().finally(() => { refreshInFlight = null })
    return refreshInFlight
  }

  async function ensureAuthenticated(): Promise<boolean> {
    if (readStorage('aibi.idSchemaVersion', '') === '2'
        && isAuthenticated.value && isFuture(tenantAccessTokenExpiresAt.value)) return true
    return refreshSession()
  }

  async function logout(): Promise<void> {
    try {
      if (accessToken.value) await authApi.logout()
    } finally {
      clearSession()
    }
  }

  function clearSession(): void {
    accessToken.value = ''
    accessTokenExpiresAt.value = ''
    refreshToken.value = ''
    refreshTokenExpiresAt.value = ''
    tenantAccessToken.value = ''
    tenantAccessTokenExpiresAt.value = ''
    tenantId.value = ''
    workspaceId.value = ''
    tenants.value = []
    memberships.value = []
    workspaces.value = []
    user.value = null
    currentMember.value = null
    ;[
      'aibi.accessToken', 'aibi.accessTokenExpiresAt', 'aibi.refreshToken',
      'aibi.refreshTokenExpiresAt', 'aibi.tenantAccessToken', 'aibi.tenantAccessTokenExpiresAt',
      'aibi.tenantId', 'aibi.memberId', 'aibi.memberships', 'aibi.workspaces',
      'aibi.workspaceId', 'aibi.tenants', 'aibi.user', 'aibi.currentMember',
    ].forEach(key => localStorage.removeItem(key))
  }

  return {
    sidebarCollapsed,
    mobileSidebarOpen,
    tenantId,
    workspaceId,
    tenants,
    memberships,
    workspaces,
    user,
    currentMember,
    isAuthenticated,
    userName,
    userEmail,
    userInitial,
    notificationCount,
    switchingTenant,
    contextVersion,
    toggleSidebar,
    setMobileSidebar,
    login,
    switchTenant,
    switchWorkspace,
    refreshSession,
    ensureAuthenticated,
    logout,
    clearSession,
  }
})
