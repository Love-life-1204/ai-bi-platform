import { ElMessage } from 'element-plus'
import { ApiClient } from './client'
import { DashboardApi } from '../dashboard/api'
import { DatasourceApi } from '../datasources/api'
import { QueryApi } from '../query/api'
import { SemanticApi } from '../semantic/api'
import { SyncApi } from '../sync/api'
import { AiApi } from '../ai/api'
import { AuthApi } from '../auth/api'
import { OrganizationApi } from './organization'
import { AnalyticsApi } from '../analytics/api'
import { CommercialApi } from '../commercial/api'

export interface ApiSessionHandlers {
  refresh: () => Promise<boolean>
  expired: () => void
  forbidden: () => void
}

let sessionHandlers: ApiSessionHandlers | undefined

export function configureApiSession(handlers: ApiSessionHandlers): void {
  sessionHandlers = handlers
}

export const apiClient = new ApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api',
  getAccessToken: () => localStorage.getItem('aibi.tenantAccessToken') ?? undefined,
  getGlobalAccessToken: () => localStorage.getItem('aibi.accessToken') ?? undefined,
  getTenantId: () => localStorage.getItem('aibi.tenantId') ?? undefined,
  getMemberId: () => localStorage.getItem('aibi.memberId') ?? undefined,
  onUnauthorized: async () => {
    const recovered = await sessionHandlers?.refresh() ?? false
    if (!recovered) sessionHandlers?.expired()
    return recovered
  },
  onForbidden: () => sessionHandlers?.forbidden(),
  onError: error => ElMessage.error({
    message: `${error.message}${error.traceId ? `（追踪号：${error.traceId}）` : ''}`,
    duration: 5_000,
  }),
})

export const datasourceApi = new DatasourceApi(apiClient)
export const syncApi = new SyncApi(apiClient)
export const semanticApi = new SemanticApi(apiClient)
export const queryApi = new QueryApi(apiClient)
export const dashboardApi = new DashboardApi(apiClient)
export const aiApi = new AiApi(apiClient)
export const authApi = new AuthApi(apiClient)
export const organizationApi = new OrganizationApi(apiClient)
export const analyticsApi = new AnalyticsApi(apiClient)
export const commercialApi = new CommercialApi(apiClient)
