/** Database IDs stay decimal strings at every browser boundary. */
export type Id = string

/** The caller supplies the reviewed DTO fields; arbitrary business row keys are never inferred. */
export function normalizeIdFields<T extends object>(value: T, fields: readonly (keyof T)[]): T {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('资源身份响应格式不合法')
  const copy = { ...value }
  for (const field of fields) copy[field] = resourceId(value[field]) as T[keyof T]
  return copy
}

export function resourceId(value: unknown): Id {
  if (typeof value === 'number') {
    if (!Number.isSafeInteger(value) || value <= 0) throw new Error('资源 ID 已失真，请重新加载授权上下文')
    return String(value)
  }
  if (typeof value !== 'string' || !/^[1-9][0-9]{0,18}$/.test(value)
      || BigInt(value) > 9223372036854775807n) {
    throw new Error('资源 ID 必须是规范的正整数字符串')
  }
  return value
}

export function resourceIds(text: string): Id[] {
  return text.split(',').map(value => value.trim()).filter(Boolean).map(resourceId)
}

export function optionalResourceId(value: unknown): Id | undefined {
  return value === undefined || value === null || value === '' ? undefined : resourceId(value)
}

export function isResourceId(value: unknown): boolean {
  try { resourceId(value); return true } catch { return false }
}

/** Upgrade safe legacy numbers and discard only the affected corrupt ID list. */
export function readStoredIds(key: string, storage: Storage = localStorage): Id[] {
  const raw = storage.getItem(key)
  if (!raw) return []
  try {
    const values: unknown = JSON.parse(raw)
    if (!Array.isArray(values)) throw new Error('ID cache is not an array')
    const result = values.map(resourceId)
    storage.setItem(key, JSON.stringify(result))
    return result
  } catch {
    storage.removeItem(key)
    return []
  }
}
