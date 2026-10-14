import { describe, expect, it, vi } from 'vitest'
import { ApiClient, ApiClientError } from './client'

describe('ApiClient', () => {
  it('企业切换等全局认证请求不携带旧租户上下文', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      code: 'SUCCESS', message: '请求成功', data: {}, trace_id: 'switch-trace',
    }), { status: 200 }))
    const client = new ApiClient({ fetchImpl, getGlobalAccessToken: () => 'global-token',
      getTenantId: () => '101', getMemberId: () => '202' })
    await client.request('/auth/switch-tenant', { method: 'POST', auth: 'global', body: { tenantId: '303' } })
    const headers = new Headers(fetchImpl.mock.calls[0]?.[1]?.headers)
    expect(headers.get('Authorization')).toBe('Bearer global-token')
    expect(headers.has('X-Tenant-Id')).toBe(false)
    expect(headers.has('X-Member-Id')).toBe(false)
  })

  it('解包成功响应并传递租户、幂等与 trace 请求头', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      code: 'SUCCESS',
      message: '请求成功',
      data: { id: '7' },
      trace_id: 'server-trace-1234',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    const loading: Array<[boolean, number]> = []
    const client = new ApiClient({
      baseUrl: 'https://api.example.test/',
      fetchImpl,
      getAccessToken: () => 'token',
      getTenantId: () => '101',
      getMemberId: () => '202',
      onLoadingChange: (active, count) => loading.push([active, count]),
    })

    await expect(client.request<{ id: number }>('/items', {
      method: 'POST',
      body: { name: '测试' },
      idempotencyKey: 'idem-test-1234',
    })).resolves.toEqual({ id: '7' })

    const [url, init] = fetchImpl.mock.calls[0]!
    const headers = new Headers(init?.headers)
    expect(url).toBe('https://api.example.test/items')
    expect(headers.get('Authorization')).toBe('Bearer token')
    expect(headers.get('X-Tenant-Id')).toBe('101')
    expect(headers.get('X-Member-Id')).toBe('202')
    expect(headers.get('Idempotency-Key')).toBe('idem-test-1234')
    expect(headers.get('X-Trace-Id')).toMatch(/^[a-f0-9]{32}$/)
    expect(loading).toEqual([[true, 1], [false, 0]])
  })

  it('统一抛出后端错误并触发错误提示回调', async () => {
    const onError = vi.fn()
    const client = new ApiClient({
      fetchImpl: vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
        code: 'CONFLICT',
        message: '资源状态冲突',
        data: null,
        trace_id: 'error-trace-1234',
      }), { status: 409 })),
      onError,
    })

    await expect(client.request('/conflict')).rejects.toMatchObject({
      code: 'CONFLICT',
      status: 409,
      traceId: 'error-trace-1234',
    } satisfies Partial<ApiClientError>)
    expect(onError).toHaveBeenCalledOnce()
  })

  it('默认以无版本号的 /api 作为公共接口根路径', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      code: 'SUCCESS',
      message: '请求成功',
      data: [],
      trace_id: 'server-trace-1234',
    }), { status: 200 }))
    const client = new ApiClient({ fetchImpl })

    await client.request('/datasources')

    expect(fetchImpl.mock.calls[0]?.[0]).toBe('/api/datasources')
  })

  it('超时时取消请求并返回统一错误', async () => {
    const fetchImpl = vi.fn<typeof fetch>((_input, init) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(init.signal?.reason), { once: true })
    }))
    const client = new ApiClient({ fetchImpl, timeoutMs: 5 })

    await expect(client.request('/slow')).rejects.toMatchObject({
      code: 'REQUEST_TIMEOUT',
      status: 0,
    } satisfies Partial<ApiClientError>)
  })

  it('会话过期时只刷新一次并重放原请求', async () => {
    const fetchImpl = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'UNAUTHORIZED', message: '请先登录或重新登录', data: null, trace_id: 'expired-trace',
      }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 'SUCCESS', message: '请求成功', data: { restored: true }, trace_id: 'retry-trace',
      }), { status: 200 }))
    const onUnauthorized = vi.fn().mockResolvedValue(true)
    const client = new ApiClient({ fetchImpl, onUnauthorized })

    await expect(client.request('/protected')).resolves.toEqual({ restored: true })
    expect(onUnauthorized).toHaveBeenCalledOnce()
    expect(fetchImpl).toHaveBeenCalledTimes(2)
  })

  it('接受退出接口的 204 空响应', async () => {
    const client = new ApiClient({
      fetchImpl: vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 })),
    })

    await expect(client.request<void>('/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('下载二进制导出文件时保留认证头和服务端文件名', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(new Response('report-data', {
      status: 200,
      headers: { 'Content-Disposition': 'attachment; filename="report.csv"' },
    }))
    const client = new ApiClient({ fetchImpl, getAccessToken: () => 'tenant-token', getTenantId: () => '7' })

    const file = await client.download('/exports/9/download')

    expect(file.fileName).toBe('report.csv')
    expect(await file.blob.text()).toBe('report-data')
    const headers = new Headers(fetchImpl.mock.calls[0]?.[1]?.headers)
    expect(headers.get('Authorization')).toBe('Bearer tenant-token')
    expect(headers.get('X-Tenant-Id')).toBe('7')
  })

  it('业务上下文切换时取消全部进行中的请求', async () => {
    const fetchImpl = vi.fn<typeof fetch>((_input, init) => new Promise((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(init.signal?.reason), { once: true })
    }))
    const client = new ApiClient({ fetchImpl })
    const first = client.request('/tenant-a/dashboard', { silent: true })
    const second = client.request('/tenant-a/metrics', { silent: true })

    client.abortAll()

    await expect(first).rejects.toMatchObject({ code: 'REQUEST_ABORTED' })
    await expect(second).rejects.toMatchObject({ code: 'REQUEST_ABORTED' })
  })
})
