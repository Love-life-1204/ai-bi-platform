/** 后端所有 JSON API 共用的响应信封。HTTP 状态码表达协议语义，code 表达稳定业务语义。 */
export interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
  trace_id: string
}

export interface FieldViolation {
  field: string
  message: string
}

export interface ValidationErrorData {
  errors: FieldViolation[]
}

export function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.code === 'string' &&
    typeof candidate.message === 'string' &&
    'data' in candidate &&
    typeof candidate.trace_id === 'string'
  )
}
