import { describe, expect, it } from 'vitest'
import { exportStatus, paymentStatus, quotaPercent } from './presentation'

describe('commercial presentation guards', () => {
  it('only treats a server-paid order with paidAt as confirmed', () => {
    expect(paymentStatus({ status: 'PENDING' }).confirmed).toBe(false)
    expect(paymentStatus({ status: 'PAID' }).confirmed).toBe(false)
    expect(paymentStatus({ status: 'PAID', paidAt: '2026-10-13T00:00:00Z' }).confirmed).toBe(true)
  })
  it('keeps exports pending until a terminal server status', () => {
    expect(exportStatus('PENDING').terminal).toBe(false)
    expect(exportStatus('RUNNING').terminal).toBe(false)
    expect(exportStatus('SUCCEEDED').terminal).toBe(true)
  })
  it('includes reservations in quota consumption and caps percent', () => {
    expect(quotaPercent({ limit: 100, used: 70, reserved: 10 })).toBe(80)
    expect(quotaPercent({ limit: 10, used: 12, reserved: 2 })).toBe(100)
  })
})
