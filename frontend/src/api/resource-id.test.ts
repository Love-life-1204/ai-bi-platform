// @vitest-environment jsdom
import { beforeEach, expect, it } from 'vitest'
import { normalizeIdFields, optionalResourceId, readStoredIds, resourceId } from './resource-id'

beforeEach(() => localStorage.clear())

it('retains all canonical BIGINT boundaries and accepts only safe legacy numbers', () => {
  for (const id of ['9007199254740991', '9007199254740992', '9007199254740993', '9223372036854775807']) {
    expect(resourceId(id)).toBe(id)
  }
  expect(resourceId(Number.MAX_SAFE_INTEGER)).toBe('9007199254740991')
  expect(optionalResourceId('')).toBeUndefined()
  for (const value of [9007199254740992, 1.5, true, null, 0, -1, '01', '+1', ' 1', '1 ', '1e3', '9223372036854775808']) {
    expect(() => resourceId(value)).toThrow()
  }
})

it('upgrades safe cached IDs and discards only the corrupt scoped list', () => {
  localStorage.setItem('safe', '[1,2]')
  localStorage.setItem('unsafe', '[9007199254740993]')
  localStorage.setItem('aibi.deviceFingerprint', 'unrelated-device')
  expect(readStoredIds('safe')).toEqual(['1', '2'])
  expect(localStorage.getItem('safe')).toBe('["1","2"]')
  expect(readStoredIds('unsafe')).toEqual([])
  expect(localStorage.getItem('unsafe')).toBeNull()
  expect(localStorage.getItem('aibi.deviceFingerprint')).toBe('unrelated-device')
})

it('normalizes only explicitly declared DTO fields without changing metrics or nested business rows', () => {
  const source = { id: 7, rowCount: 8, rows: [{ id: 9 }] }
  const result = normalizeIdFields(source, ['id'])
  expect(result).toEqual({ id: '7', rowCount: 8, rows: [{ id: 9 }] })
  expect(source.id).toBe(7)
})
