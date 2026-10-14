import { describe, expect, it, vi } from 'vitest'
import { DatasourceApi } from './api'

describe('数据源和文件可信工作空间请求', () => {
  it('草稿连接测试传递显式工作空间', async () => {
    const request = vi.fn().mockResolvedValue({})
    const api = new DatasourceApi({ request })
    await api.testDraft({ workspaceId: '42', code: 'source', name: '测试', type: 'MYSQL',
      endpoint: { host: 'db.example.invalid', port: 3306, database: 'sales', tls: true },
      credential: { username: 'fake', password: 'test-only' } })
    expect(request.mock.calls[0]?.[1].body.workspaceId).toBe('42')
  })

  it('文件上传携带当前选择的工作空间', async () => {
    const request = vi.fn().mockResolvedValue({})
    const api = new DatasourceApi({ request })
    const file = new File(['id\nA\n'], 'orders.csv', { type: 'text/csv' })
    await api.uploadFile(file, '42')
    const [path, options] = request.mock.calls[0]!
    expect(path).toBe('/files/uploads')
    expect(options.body.get('workspaceId')).toBe('42')
    expect(options.body.get('file')).toBe(file)
  })
})
