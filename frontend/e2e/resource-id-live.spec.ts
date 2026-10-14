import { expect, test } from '@playwright/test'
import { writeFile } from 'node:fs/promises'

test.skip(!process.env.REPAIR_API_BASE, 'Requires an owned ephemeral test JVM')

test('真实隔离 HTTP：大 ID 登录、跨企业切换及刷新保持精确授权上下文', async ({ page, request }) => {
  const base = process.env.REPAIR_API_BASE!
  expect(base).toMatch(/^http:\/\/127\.0\.0\.1:\d+$/)
  const suffix = crypto.randomUUID().replaceAll('-', '')
  const email = `browser_${suffix}@example.invalid`
  const password = 'isolated-browser-only-password-2026'
  async function call(path: string, data?: unknown, token?: string, tenant?: string) {
    const response = await request.post(base + path, {
      ...(data === undefined ? {} : { data }),
      headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(tenant ? { 'X-Tenant-Id': tenant } : {}) },
    })
    expect(response.ok()).toBeTruthy()
    const body = await response.json()
    expect(body.code).toBe('SUCCESS')
    return body.data
  }
  await call('/api/auth/register', { email, displayName: '隔离浏览器测试', password })
  const global = await call('/api/auth/login', { email, password })
  const tenants: Array<{ id: string; name: string; workspaceId: string }> = []
  for (const label of ['甲', '乙']) {
    const tenant = await call('/api/tenants', { tenantCode: `browser_${label === '甲' ? 'a' : 'b'}_${suffix}`, tenantName: `隔离浏览器${label}` }, global.accessToken)
    expect(typeof tenant.id).toBe('string')
    expect(BigInt(tenant.id) > BigInt(Number.MAX_SAFE_INTEGER)).toBeTruthy()
    const context = await call('/api/auth/switch-tenant', { tenantId: tenant.id }, global.accessToken)
    const workspace = await call(`/api/tenants/${tenant.id}/workspaces`, { workspaceCode: 'browser_main', workspaceName: `隔离工作空间${label}` }, context.tenantAccessToken, tenant.id)
    expect(typeof workspace.id).toBe('string')
    expect(BigInt(workspace.id) > BigInt(Number.MAX_SAFE_INTEGER)).toBeTruthy()
    tenants.push({ id: tenant.id, name: `隔离浏览器${label}`, workspaceId: workspace.id })
  }
  const switches: string[] = []
  const scopes: Array<{ tenantId: string; workspaceId: string }> = []
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  // Forward unchanged request bytes to the real test JVM; no fabricated API responses.
  await page.route(/^http:\/\/127\.0\.0\.1:\d+\/api\//, async route => {
    const original = route.request()
    const url = new URL(original.url())
    if (url.pathname === '/api/auth/switch-tenant') {
      const payload = original.postDataJSON()
      expect(typeof payload.tenantId).toBe('string')
      expect(tenants.map(tenant => tenant.id)).toContain(payload.tenantId)
      expect(original.headers()['x-tenant-id']).toBeUndefined()
      switches.push(payload.tenantId)
    }
    if (url.pathname === '/api/datasources' && original.method() === 'GET') {
      scopes.push({ tenantId: original.headers()['x-tenant-id']!, workspaceId: url.searchParams.get('workspaceId')! })
    }
    await route.fulfill({ response: await route.fetch({ url: base + url.pathname + url.search }) })
  })
  await page.goto('/login')
  await page.getByPlaceholder('name@company.com').fill(email)
  await page.getByPlaceholder('请输入登录密码').fill(password)
  await page.getByRole('button', { name: '登录工作台' }).click()
  await expect(page.getByRole('combobox', { name: '切换企业' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('aibi.tenantId'))).toMatch(/^[1-9][0-9]+$/)
  const first = await page.evaluate(() => localStorage.getItem('aibi.tenantId'))
  const other = tenants.find(tenant => tenant.id !== first)!
  expect(tenants.map(tenant => tenant.id)).toContain(first)
  await page.locator('.tenant-select').click()
  await page.getByRole('option', { name: other.name, exact: true }).click()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('aibi.tenantId'))).toBe(other.id)
  await expect.poll(() => page.evaluate(() => localStorage.getItem('aibi.workspaceId'))).toBe(other.workspaceId)
  await page.goto('/datasources')
  await page.reload()
  await expect(page.getByRole('combobox', { name: '切换企业' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('aibi.tenantId'))).toBe(other.id)
  expect(await page.evaluate(() => localStorage.getItem('aibi.workspaceId'))).toBe(other.workspaceId)
  await expect.poll(() => scopes.some(scope => scope.tenantId === other.id && scope.workspaceId === other.workspaceId)).toBeTruthy()
  expect(switches).toContain(other.id)
  expect(errors).toEqual([])
  await writeFile(`../testing/reports/evidence/autonomous-20261014/${process.env.REPAIR_BROWSER_LABEL ?? 'd09-live-browser'}-id-evidence.json`,
    JSON.stringify({ environment: 'owned ephemeral JVM, in-memory SQL stores; actual browser and HTTP, no API mocks', tenants, switches, scopes, pageErrors: errors }, null, 2))
})
