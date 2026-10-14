import { describe, expect, it } from 'vitest'
import { isTaskActive, rowProgress, taskStatusLabel, taskStatusTone } from './status'

describe('sync task status', () => {
  it('keeps accepted work pending until backend reports a terminal state', () => {
    expect(isTaskActive('PENDING')).toBe(true)
    expect(isTaskActive('RUNNING')).toBe(true)
    expect(isTaskActive('SUCCESS')).toBe(false)
  })

  it('maps partial, failure and dead-letter states explicitly', () => {
    expect(taskStatusLabel('PARTIAL_SUCCESS')).toBe('部分成功')
    expect(taskStatusTone('FAILED')).toBe('danger')
    expect(taskStatusLabel('FAILED', true)).toBe('死信待补偿')
  })

  it('derives progress from backend row counters', () => {
    expect(rowProgress(10, 6, 2, 'RUNNING')).toBe(80)
    expect(rowProgress(10, 8, 2, 'PARTIAL_SUCCESS')).toBe(100)
    expect(rowProgress(0, 0, 0, 'PENDING')).toBe(0)
  })
})
