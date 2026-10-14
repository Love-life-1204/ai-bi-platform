import { describe, expect, it } from 'vitest'
import { ApiClientError } from '../api/client'
import { connectionFailure, connectionSuccess } from './connection-status'

describe('connection status', () => {
  it('shows success details', () => {
    expect(connectionSuccess({ status: 'SUCCESS', latencyMs: 18, databaseVersion: 'MySQL 8', message: 'ok' }))
      .toEqual({ kind: 'success', message: '连接成功 · MySQL 8', latencyMs: 18 })
  })

  it.each([
    ['DATASOURCE_AUTH_FAILED', 'auth-failed'],
    ['DATASOURCE_NETWORK_TIMEOUT', 'timeout'],
    ['DATASOURCE_PERMISSION_DENIED', 'permission-denied'],
    ['DATASOURCE_TARGET_BLOCKED', 'target-blocked'],
    ['FORBIDDEN', 'forbidden'],
    ['NETWORK_ERROR', 'unavailable'],
  ] as const)('maps %s to %s', (code, kind) => {
    const error = new ApiClientError({ code, message: 'server message', status: 400 })
    expect(connectionFailure(error).kind).toBe(kind)
  })

  it('maps service unavailable response', () => {
    const error = new ApiClientError({ code: 'UPSTREAM_UNAVAILABLE', message: 'unavailable', status: 503 })
    expect(connectionFailure(error).kind).toBe('unavailable')
  })
})
