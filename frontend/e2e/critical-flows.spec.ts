import { expect, test, type Page, type Route } from '@playwright/test'

const now = '2026-10-13T08:00:00.000Z'
const future = '2099-12-31T23:59:59.000Z'
const member = { id: 7, tenantId: 1, userId: 9, memberName: '验收成员', memberStatus: 'ACTIVE', owner: true, permissionVersion: 1, joinedAt: now, lockVersion: 0 }
const workspace = { id: 1, tenantId: 1, workspaceCode: 'main', workspaceName: '验收工作空间', workspaceStatus: 'ACTIVE', ownerMemberId: 7, lockVersion: 0 }
const user = { id: 9, email: 'qa@example.com', displayName: '验收用户', userStatus: 'ACTIVE', createdAt: now }

test.beforeEach(async ({ page }) => {
  page.on('pageerror', error => console.error(`[pageerror] ${error.message}`))
  page.on('console', message => {
    if (message.type() === 'error') console.error(`[console] ${message.text()}`)
  })
  page.on('requestfailed', request => console.error(`[requestfailed] ${request.url()} ${request.failure()?.errorText ?? ''}`))
})

function envelope(data: unknown) {
  return { code: 'SUCCESS', message: '请求成功', data, trace_id: 'e2e-trace' }
}

async function fulfill(route: Route, data: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(envelope(data)) })
}

async function seedSession(page: Page): Promise<void> {
  await page.addInitScript(({ memberData, workspaceData, userData, expiresAt }) => {
    const text: Record<string, string> = {
      'aibi.accessToken': 'global-token',
      'aibi.accessTokenExpiresAt': expiresAt,
      'aibi.refreshToken': 'refresh-token',
      'aibi.refreshTokenExpiresAt': expiresAt,
      'aibi.tenantAccessToken': 'tenant-token',
      'aibi.tenantAccessTokenExpiresAt': expiresAt,
      'aibi.tenantId': '1',
      'aibi.memberId': String(memberData.id),
      'aibi.workspaceId': String(workspaceData.id),
      'aibi.currentMember': JSON.stringify(memberData),
      'aibi.memberships': JSON.stringify([memberData]),
      'aibi.workspaces': JSON.stringify([workspaceData]),
      'aibi.tenants': JSON.stringify([{ id: '1', name: '验收企业', code: 'qa' }]),
      'aibi.user': JSON.stringify(userData),
    }
    Object.entries(text).forEach(([key, value]) => localStorage.setItem(key, value))
  }, { memberData: member, workspaceData: workspace, userData: user, expiresAt: future })
}

async function mockFallback(page: Page): Promise<void> {
  await page.route(/^http:\/\/127\.0\.0\.1:4173\/api\//, async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/tenants/1/members') return fulfill(route, [member])
    if (request.method() === 'GET') return fulfill(route, [])
    return fulfill(route, {})
  })
}

test('登录校验、回车提交与企业上下文建立', async ({ page }) => {
  await page.route(/^http:\/\/127\.0\.0\.1:4173\/api\//, async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/login') return fulfill(route, { tokenType: 'Bearer', accessToken: 'global-token', accessTokenExpiresAt: future, refreshToken: 'refresh-token', refreshTokenExpiresAt: future, user })
    if (path === '/api/users/me/memberships') return fulfill(route, [member])
    if (path === '/api/users/me/tenants') {
      expect(route.request().headers()['authorization']).toBe('Bearer global-token')
      expect(route.request().headers()['x-tenant-id']).toBeUndefined()
      return fulfill(route, [{ id: 1, tenantCode: 'qa', tenantName: '验收企业', tenantStatus: 'ACTIVE' }])
    }
    if (path === '/api/tenants/1') return fulfill(route, { id: 1, tenantCode: 'qa', tenantName: '验收企业', tenantStatus: 'ACTIVE', timezone: 'Asia/Shanghai', defaultCurrency: 'CNY', permissionVersion: 1, createdAt: now, updatedAt: now, lockVersion: 0 })
    if (path === '/api/auth/switch-tenant') return fulfill(route, { tokenType: 'Bearer', tenantAccessToken: 'tenant-token', expiresAt: future, tenantId: 1, memberId: 7, tenantPermissionVersion: 1, memberPermissionVersion: 1 })
    if (path === '/api/tenants/1/workspaces') return fulfill(route, [workspace])
    if (route.request().method() === 'GET') return fulfill(route, [])
    return fulfill(route, {})
  })
  await page.goto('/login')
  await page.getByPlaceholder('name@company.com').fill('not-an-email')
  await page.getByPlaceholder('请输入登录密码').fill('short')
  await page.getByRole('button', { name: '登录工作台' }).click()
  await expect(page.getByText('请输入有效的邮箱地址')).toBeVisible()
  await page.getByPlaceholder('name@company.com').fill('qa@example.com')
  await page.getByPlaceholder('请输入登录密码').fill('correct-password')
  await page.getByPlaceholder('请输入登录密码').press('Enter')
  await expect(page).toHaveURL(/\/workspace$/)
  await expect(page.getByText('验收工作空间').first()).toBeVisible()
})

test('创建数据源时不回显凭据且提交完整连接参数', async ({ page }) => {
  await seedSession(page)
  await mockFallback(page)
  let createdBody: Record<string, unknown> | undefined
  const sources: unknown[] = []
  await page.route(/^http:\/\/127\.0\.0\.1:4173\/api\//, async route => {
    const request = route.request(); const path = new URL(request.url()).pathname
    if (path === '/api/datasources' && request.method() === 'GET') return fulfill(route, sources)
    if (path === '/api/datasources' && request.method() === 'POST') {
      createdBody = request.postDataJSON() as Record<string, unknown>
      const input = createdBody as Record<string, any>
      sources.push({ id: 11, workspaceId: 1, code: input.code, name: input.name, type: input.type, endpoint: input.endpoint, hasCredential: true, ownerMemberId: 7, status: 'ACTIVE', createdAt: now, updatedAt: now, lockVersion: 0 })
      return fulfill(route, sources[0])
    }
    if (path === '/api/tenants/1/members') return fulfill(route, [member])
    return route.fallback()
  })
  await page.goto('/datasources')
  await page.getByRole('button', { name: '新增数据源' }).first().click()
  await page.getByRole('button', { name: /MySQL/ }).click()
  await page.getByRole('button', { name: '继续' }).click()
  await page.getByPlaceholder('例如：销售订单库').fill('自动化销售库')
  await page.getByPlaceholder('例如：sales_orders').fill('e2e_sales')
  await page.getByPlaceholder('db.example.com').fill('db.internal.example')
  await page.getByPlaceholder('analytics').fill('analytics')
  await page.getByPlaceholder('只读账号').fill('readonly_user')
  await page.getByPlaceholder('请输入密码').fill('secret-not-returned')
  await page.getByRole('button', { name: '保存数据源' }).last().click()
  await expect(page.getByText('自动化销售库')).toBeVisible()
  expect(createdBody).toMatchObject({ code: 'e2e_sales', type: 'MYSQL', credential: { username: 'readonly_user', password: 'secret-not-returned' } })
  await expect(page.getByText('secret-not-returned')).toHaveCount(0)
})

test('从采集元数据创建数据集草稿', async ({ page }) => {
  await seedSession(page)
  await mockFallback(page)
  let datasetBody: Record<string, unknown> | undefined
  const datasource = { id: 11, workspaceId: 1, code: 'sales', name: '销售库', type: 'MYSQL', endpoint: { host: 'db', port: 3306, database: 'sales', tls: true }, hasCredential: true, ownerMemberId: 7, status: 'ACTIVE', createdAt: now, updatedAt: now, lockVersion: 0 }
  const snapshot = { id: 21, datasourceId: 11, version: 1, schemaHash: 'schema-1', collectedAt: now, objects: [{ id: 31, schema: 'public', name: 'orders', type: 'TABLE', schemaHash: 'orders-1', columns: [{ id: 41, name: 'order_id', stableKey: 'order_id', ordinalPosition: 1, dataType: 'BIGINT', nullable: false, primaryKey: true }, { id: 42, name: 'amount', stableKey: 'amount', ordinalPosition: 2, dataType: 'DECIMAL', nullable: false, primaryKey: false }] }] }
  const datasets: unknown[] = []
  await page.route(/^http:\/\/127\.0\.0\.1:4173\/api\//, async route => {
    const request = route.request(); const path = new URL(request.url()).pathname
    if (path === '/api/datasets' && request.method() === 'GET') return fulfill(route, datasets)
    if (path === '/api/datasets' && request.method() === 'POST') {
      datasetBody = request.postDataJSON() as Record<string, unknown>
      const item = { id: 51, workspaceId: 1, code: 'orders_model', name: '订单模型', status: 'DRAFT', ownerMemberId: 7, createdAt: now, updatedAt: now, lockVersion: 0 }
      datasets.push(item)
      return fulfill(route, { dataset: item, draft: { id: 52, versionNo: 1, sourceObjectId: 31, fields: [], relations: [], previewRows: [], definitionHash: 'draft', status: 'DRAFT', lockVersion: 0 } })
    }
    if (path === '/api/datasources') return fulfill(route, [datasource])
    if (path === '/api/datasources/11/metadata/snapshots') return fulfill(route, [snapshot])
    if (/\/api\/datasets\/\d+\/versions$/.test(path)) return fulfill(route, [])
    return route.fallback()
  })
  await page.goto('/semantic')
  await page.getByRole('button', { name: '新建数据集' }).first().click()
  await page.getByPlaceholder('例如：销售订单明细').fill('订单模型')
  await page.getByPlaceholder('sales_orders').fill('orders_model')
  await page.getByRole('combobox', { name: '来源表' }).click()
  await page.getByText('销售库 · public.orders').click()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByText('订单模型').first()).toBeVisible()
  expect(datasetBody).toMatchObject({ code: 'orders_model', sourceObjectId: 31 })
  expect((datasetBody?.fields as unknown[]).length).toBe(2)
})

test('创建看板草稿并在移动端保持只读', async ({ page }) => {
  await seedSession(page)
  await mockFallback(page)
  let dashboardBody: Record<string, unknown> | undefined
  const chart = { id: 61, tenantId: 1, workspaceId: 1, code: 'sales_bar', name: '销售柱状图', currentVersionId: 62, status: 'PUBLISHED', ownerMemberId: 7, createdAt: now, updatedAt: now, lockVersion: 0 }
  await page.route(/^http:\/\/127\.0\.0\.1:4173\/api\//, async route => {
    const request = route.request(); const path = new URL(request.url()).pathname
    if (path === '/api/charts') return fulfill(route, [chart])
    if (path === '/api/dashboards' && request.method() === 'GET') return fulfill(route, [])
    if (path === '/api/dashboards' && request.method() === 'POST') {
      dashboardBody = request.postDataJSON() as Record<string, unknown>
      const dashboard = { id: 71, tenantId: 1, workspaceId: 1, code: 'e2e_board', name: '自动化看板', ownerMemberId: 7, status: 'DRAFT', createdAt: now, updatedAt: now, lockVersion: 0 }
      const draft = { id: 72, dashboardId: 71, versionNo: 1, layout: { columns: 12, rowHeight: 72 }, filters: [], widgets: [], status: 'DRAFT', lockVersion: 0 }
      return fulfill(route, { dashboard, draft })
    }
    if (path === '/api/dashboards/71/query') return fulfill(route, { dashboardId: 71, dashboardVersionId: 72, mode: 'PREVIEW', widgets: [] })
    return route.fallback()
  })
  await page.goto('/dashboards')
  await page.getByRole('button', { name: '新建看板' }).first().click()
  await page.getByPlaceholder('例如：经营总览').fill('自动化看板')
  await page.getByPlaceholder('business_overview').fill('e2e_board')
  await page.getByRole('button', { name: '创建并编辑' }).click()
  await expect(page.getByText('自动化看板')).toBeVisible()
  expect(dashboardBody).toMatchObject({ code: 'e2e_board', layout: { columns: 12, rowHeight: 72 } })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByText(/移动端为只读看板/)).toBeVisible()
  await expect(page.getByRole('button', { name: '保存布局' })).toHaveCount(0)
})

test('AI 问题通过 SSE 展示真实结果分类与任务轨迹', async ({ page }) => {
  await seedSession(page)
  await mockFallback(page)
  const conversation = { id: 81, workspaceId: 1, memberId: 7, title: '经营分析', status: 'ACTIVE', contextVersion: 1, createdAt: now, updatedAt: now }
  await page.route(/^http:\/\/127\.0\.0\.1:4173\/api\//, async route => {
    const request = route.request(); const path = new URL(request.url()).pathname
    if (path === '/api/ai/conversations' && request.method() === 'GET') return fulfill(route, [conversation])
    if (path === '/api/ai/conversations/81') return fulfill(route, conversation)
    if (path === '/api/ai/conversations/81/messages' && request.method() === 'GET') return fulfill(route, [])
    if (path === '/api/ai/conversations/81/messages' && request.method() === 'POST') return fulfill(route, { requestId: 'req-1', taskId: 'task-1', status: 'ACCEPTED', eventsUrl: '/api/ai/tasks/task-1/events' })
    if (path === '/api/ai/tasks/task-1/events') {
      const result = { answer: { summary: '近 30 天销售额为 128 万元', facts: [{ statement: '销售额', value: 128, unit: '万元', query_id: 'query-1' }], assumptions: ['按已发布净销售额口径'], recommendations: ['关注华东区域增长'], limitations: [] }, evidence: [] }
      const body = `id: 1\nevent: accepted\ndata: {"status":"ACCEPTED"}\n\nid: 2\nevent: progress\ndata: {"stage":"querying"}\n\nid: 3\nevent: result\ndata: ${JSON.stringify(result)}\n\nid: 4\nevent: done\ndata: {"status":"SUCCEEDED"}\n\n`
      return route.fulfill({ status: 200, contentType: 'text/event-stream', body })
    }
    if (path === '/api/ai/tasks/task-1/evidence') return fulfill(route, [])
    if (path === '/api/datasets') return fulfill(route, [])
    return route.fallback()
  })
  await page.goto('/ai')
  await page.getByPlaceholder('例如：最近 30 天各地区净销售额是多少？').fill('最近 30 天销售额是多少？')
  await page.getByRole('button', { name: '发送问题' }).click()
  await expect(page.getByRole('heading', { name: '近 30 天销售额为 128 万元' })).toBeVisible()
  await expect(page.getByText('按已发布净销售额口径')).toBeVisible()
  await expect(page.getByText('关注华东区域增长')).toBeVisible()
  await expect(page.getByText('task_id task-1')).toBeVisible()
})
