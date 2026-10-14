import { describe, expect, it, vi } from 'vitest'
import { AuthApi } from './api'
import type { ApiClient } from '../api/client'

describe('企业元数据身份契约', () => {
  it('详情使用当前租户身份，企业摘要使用全局会话', async () => {
    const request = vi.fn().mockResolvedValueOnce({ id: '42' }).mockResolvedValueOnce([])
    const api = new AuthApi({ request } as unknown as ApiClient)
    await api.tenant('42')
    expect(request).toHaveBeenLastCalledWith('/tenants/42')
    await api.tenantSummaries()
    expect(request).toHaveBeenLastCalledWith('/users/me/tenants', { auth: 'global' })
  })
})
