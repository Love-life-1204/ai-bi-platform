import type { Id } from '../api/resource-id'
import { isApiResponse, type ApiResponse } from './types'

export interface ApiClientErrorOptions {
  code: string
  message: string
  status: number
  traceId?: string
  data?: unknown
  cause?: unknown
}

export class ApiClientError extends Error {
  readonly code: string
  readonly status: number
  readonly traceId: string | undefined
  readonly data: unknown

  constructor(options: ApiClientErrorOptions) {
    super(options.message, { cause: options.cause })
    this.name = 'ApiClientError'
    this.code = options.code
    this.status = options.status
    this.traceId = options.traceId
    this.data = options.data
  }
}

export interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  timeoutMs?: number
  silent?: boolean
  idempotencyKey?: string
  auth?: 'default' | 'global' | 'none'
  skipAuthRefresh?: boolean
}

export interface ApiClientOptions {
  baseUrl?: string
  timeoutMs?: number
  fetchImpl?: typeof fetch
  getAccessToken?: () => string | undefined
  getGlobalAccessToken?: () => string | undefined
  getTenantId?: () => string | undefined
  getMemberId?: () => string | undefined
  onUnauthorized?: (error: ApiClientError) => Promise<boolean>
  onForbidden?: (error: ApiClientError) => void
  onError?: (error: ApiClientError) => void
  onLoadingChange?: (loading: boolean, pendingCount: number) => void
}

export interface DownloadedFile {
  blob: Blob
  fileName: string
}

const DEFAULT_TIMEOUT_MS = 15_000
const DEFAULT_API_BASE_URL = '/api'

/** 统一处理超时、响应信封、错误通知、Loading 计数和链路标识。 */
export class ApiClient {
  private readonly options: ApiClientOptions
  private readonly fetchImpl: typeof fetch
  private pendingCount = 0
  private readonly controllers = new Set<AbortController>()

  constructor(options: ApiClientOptions = {}) {
    this.options = options
    // Native fetch requires its Window/Worker global as the receiver in some browsers.
    this.fetchImpl = options.fetchImpl ?? globalThis.fetch.bind(globalThis)
  }

  async request<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
    return this.execute<T>(path, options, true)
  }

  async download(path: string, options: ApiRequestOptions = {}): Promise<DownloadedFile> {
    return this.executeDownload(path, options, true)
  }

  private async executeDownload(path: string, options: ApiRequestOptions, allowAuthRetry: boolean): Promise<DownloadedFile> {
    const controller = new AbortController()
    this.controllers.add(controller)
    const detachSignal = this.forwardAbort(options.signal, controller)
    const timeout = setTimeout(() => controller.abort(new DOMException('请求超时', 'TimeoutError')), options.timeoutMs ?? this.options.timeoutMs ?? DEFAULT_TIMEOUT_MS)
    this.updateLoading(1)
    try {
      const response = await this.fetchImpl(this.resolveUrl(path), {
        method: options.method ?? 'GET', headers: this.buildHeaders(options), signal: controller.signal,
      })
      if (!response.ok) {
        const payload = await this.readPayload(response)
        const error = isApiResponse(payload)
          ? new ApiClientError({ code: payload.code, message: payload.message, status: response.status, traceId: payload.trace_id, data: payload.data })
          : new ApiClientError({ code: 'DOWNLOAD_FAILED', message: '文件下载失败', status: response.status })
        if (response.status === 401 && allowAuthRetry && !options.skipAuthRefresh && this.options.onUnauthorized && await this.options.onUnauthorized(error)) {
          return this.executeDownload(path, options, false)
        }
        throw error
      }
      const disposition = response.headers.get('Content-Disposition') ?? ''
      const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
      const plain = disposition.match(/filename="?([^";]+)"?/i)?.[1]
      return { blob: await response.blob(), fileName: encoded ? decodeURIComponent(encoded) : plain ?? 'download' }
    } catch (cause) {
      const error = this.normalizeError(cause, controller.signal)
      if (!options.silent) this.options.onError?.(error)
      throw error
    } finally {
      clearTimeout(timeout); this.controllers.delete(controller); detachSignal(); this.updateLoading(-1)
    }
  }

  private async execute<T>(path: string, options: ApiRequestOptions, allowAuthRetry: boolean): Promise<T> {
    const timeoutMs = options.timeoutMs ?? this.options.timeoutMs ?? DEFAULT_TIMEOUT_MS
    const {
      body,
      timeoutMs: _timeoutMs,
      silent: _silent,
      idempotencyKey: _idempotencyKey,
      auth: _auth,
      skipAuthRefresh: _skipAuthRefresh,
      ...requestInit
    } = options
    const controller = new AbortController()
    this.controllers.add(controller)
    const detachSignal = this.forwardAbort(options.signal, controller)
    const timeout = setTimeout(() => controller.abort(new DOMException('请求超时', 'TimeoutError')), timeoutMs)
    this.updateLoading(1)

    try {
      const init: RequestInit = {
        ...requestInit,
        headers: this.buildHeaders(options),
        signal: controller.signal,
      }
      const serializedBody = this.serializeBody(body)
      if (serializedBody !== undefined) init.body = serializedBody
      const response = await this.fetchImpl(this.resolveUrl(path), init)
      if (response.status === 204 && response.ok) return undefined as T
      const payload: unknown = await this.readPayload(response)
      if (!isApiResponse(payload)) {
        throw new ApiClientError({
          code: 'INVALID_API_RESPONSE',
          message: '服务端返回格式不正确',
          status: response.status,
        })
      }
      return this.unwrap<T>(payload, response.status)
    } catch (cause) {
      const error = this.normalizeError(cause, controller.signal)
      if (error.status === 401 && allowAuthRetry && !options.skipAuthRefresh
          && options.auth !== 'none' && this.options.onUnauthorized) {
        const recovered = await this.options.onUnauthorized(error)
        if (recovered) return this.execute<T>(path, options, false)
      }
      if (error.status === 403 && (options.auth === undefined || options.auth === 'default')) {
        this.options.onForbidden?.(error)
      }
      if (!options.silent) this.options.onError?.(error)
      throw error
    } finally {
      clearTimeout(timeout)
      this.controllers.delete(controller)
      detachSignal()
      this.updateLoading(-1)
    }
  }

  abortAll(reason = '业务上下文已切换'): void {
    for (const controller of this.controllers) controller.abort(new DOMException(reason, 'AbortError'))
    this.controllers.clear()
  }

  /** 发起需要认证头的长连接请求；调用方负责消费并关闭响应流。 */
  async requestStream(path: string, signal: AbortSignal, lastEventId?: string): Promise<Response> {
    return this.executeStream(path, signal, lastEventId, true)
  }

  private async executeStream(
    path: string,
    signal: AbortSignal,
    lastEventId: string | undefined,
    allowAuthRetry: boolean,
  ): Promise<Response> {
    const headers = this.buildHeaders({
      method: 'GET', signal,
      ...(lastEventId ? { headers: { 'Last-Event-ID': lastEventId } } : {}),
    })
    const response = await this.fetchImpl(this.resolveUrl(path), { method: 'GET', headers, signal })
    if (!response.ok) {
      const payload = await this.readPayload(response)
      if (response.status === 401 && allowAuthRetry && this.options.onUnauthorized) {
        const error = isApiResponse(payload)
          ? new ApiClientError({ code: payload.code, message: payload.message, status: 401, traceId: payload.trace_id })
          : new ApiClientError({ code: 'UNAUTHORIZED', message: '登录状态已过期', status: 401 })
        if (await this.options.onUnauthorized(error)) {
          return this.executeStream(path, signal, lastEventId, false)
        }
      }
      if (isApiResponse(payload)) this.unwrap(payload, response.status)
      throw new ApiClientError({
        code: 'SSE_CONNECT_FAILED', message: '无法连接任务事件流', status: response.status,
      })
    }
    return response
  }

  private unwrap<T>(payload: ApiResponse<unknown>, status: number): T {
    if (status < 200 || status >= 300 || payload.code !== 'SUCCESS') {
      throw new ApiClientError({
        code: payload.code,
        message: payload.message,
        status,
        traceId: payload.trace_id,
        data: payload.data,
      })
    }
    return payload.data as T
  }

  private buildHeaders(options: ApiRequestOptions): Headers {
    const headers = new Headers(options.headers)
    if (options.body !== undefined && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    const token = options.auth === 'none'
      ? undefined
      : options.auth === 'global'
        ? this.options.getGlobalAccessToken?.()
        : this.options.getAccessToken?.()
    const tenantId = this.options.getTenantId?.()
    const memberId = this.options.getMemberId?.()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    // 全局令牌不绑定企业，携带旧企业头会触发后端租户令牌校验失败。
    const tenantScoped = options.auth === undefined || options.auth === 'default'
    if (tenantScoped && tenantId) headers.set('X-Tenant-Id', tenantId)
    if (tenantScoped && memberId) headers.set('X-Member-Id', memberId)
    if (options.idempotencyKey) headers.set('Idempotency-Key', options.idempotencyKey)
    if (!headers.has('X-Trace-Id')) headers.set('X-Trace-Id', this.createTraceId())
    return headers
  }

  private serializeBody(body: unknown): BodyInit | null | undefined {
    if (body === undefined || body === null || typeof body === 'string'
        || body instanceof Blob || body instanceof FormData || body instanceof URLSearchParams) {
      return body as BodyInit | null | undefined
    }
    return JSON.stringify(body)
  }

  private async readPayload(response: Response): Promise<unknown> {
    const text = await response.text()
    if (!text) {
      throw new ApiClientError({
        code: 'EMPTY_API_RESPONSE',
        message: '服务端未返回响应内容',
        status: response.status,
      })
    }
    try {
      return JSON.parse(text) as unknown
    } catch (cause) {
      throw new ApiClientError({
        code: 'INVALID_API_RESPONSE',
        message: '服务端返回格式不正确',
        status: response.status,
        cause,
      })
    }
  }

  private normalizeError(cause: unknown, signal: AbortSignal): ApiClientError {
    if (cause instanceof ApiClientError) return cause
    if (signal.aborted) {
      const timeout = signal.reason instanceof DOMException && signal.reason.name === 'TimeoutError'
      return new ApiClientError({
        code: timeout ? 'REQUEST_TIMEOUT' : 'REQUEST_ABORTED',
        message: timeout ? '请求超时，请稍后重试' : '请求已取消',
        status: 0,
        cause,
      })
    }
    return new ApiClientError({
      code: 'NETWORK_ERROR',
      message: '网络连接失败，请检查网络后重试',
      status: 0,
      cause,
    })
  }

  private forwardAbort(source: AbortSignal | null | undefined, target: AbortController): () => void {
    if (!source) return () => undefined
    const abort = () => target.abort(source.reason)
    if (source.aborted) abort()
    else source.addEventListener('abort', abort, { once: true })
    return () => source.removeEventListener('abort', abort)
  }

  private updateLoading(delta: 1 | -1): void {
    this.pendingCount = Math.max(0, this.pendingCount + delta)
    this.options.onLoadingChange?.(this.pendingCount > 0, this.pendingCount)
  }

  private resolveUrl(path: string): string {
    if (/^https?:\/\//i.test(path)) return path
    return `${(this.options.baseUrl ?? DEFAULT_API_BASE_URL).replace(/\/$/, '')}/${path.replace(/^\//, '')}`
  }

  private createTraceId(): string {
    return globalThis.crypto.randomUUID().replaceAll('-', '')
  }
}
