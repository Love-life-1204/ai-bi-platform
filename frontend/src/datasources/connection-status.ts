import { ApiClientError } from '../api/client'
import type { ConnectionTestResult, ConnectionTestState } from './types'

export function connectionSuccess(result: ConnectionTestResult): ConnectionTestState {
  return {
    kind: 'success',
    message: `连接成功 · ${result.databaseVersion}`,
    latencyMs: result.latencyMs,
  }
}

export function connectionFailure(error: unknown): ConnectionTestState {
  if (!(error instanceof ApiClientError)) return { kind: 'failed', message: '连接测试失败，请稍后重试' }
  switch (error.code) {
    case 'DATASOURCE_AUTH_FAILED':
      return { kind: 'auth-failed', message: '认证失败，请检查用户名和密码' }
    case 'DATASOURCE_NETWORK_TIMEOUT':
    case 'REQUEST_TIMEOUT':
      return { kind: 'timeout', message: '网络连接超时，请检查地址、防火墙和端口' }
    case 'DATASOURCE_PERMISSION_DENIED':
      return { kind: 'permission-denied', message: '账号包含写权限，请改用只读账号' }
    case 'DATASOURCE_TARGET_BLOCKED':
      return { kind: 'target-blocked', message: error.message }
    case 'FORBIDDEN':
      return { kind: 'forbidden', message: '当前成员没有测试数据源连接的权限' }
    case 'NETWORK_ERROR':
    case 'INTERNAL_SERVER_ERROR':
      return { kind: 'unavailable', message: '连接测试服务暂时不可用，请稍后重试' }
    default:
      if (error.status === 503 || error.status === 502) {
        return { kind: 'unavailable', message: '连接测试服务暂时不可用，请稍后重试' }
      }
      return { kind: 'failed', message: error.message || '连接测试失败' }
  }
}
