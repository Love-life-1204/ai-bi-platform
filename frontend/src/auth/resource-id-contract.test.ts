import { expect, it, vi } from 'vitest'
import { ApiClient } from '../api/client'
import { AuthApi } from './api'

function api(body: string): AuthApi {
  return new AuthApi(new ApiClient({ fetchImpl: vi.fn<typeof fetch>().mockResolvedValue(
    new Response(`{"code":"SUCCESS","message":"ok","trace_id":"id-test","data":${body}}`, { status: 200 }),
  ) }))
}

it('normalizes safe legacy numeric membership IDs to strings while keeping versions numeric', async () => {
  const client = api('[{"id":7,"tenantId":11,"userId":13,"permissionVersion":2,"lockVersion":0}]')
  const memberships = await client.memberships()
  expect(memberships[0]).toMatchObject({ id: '7', tenantId: '11', userId: '13', permissionVersion: 2, lockVersion: 0 })
})

it('rejects an unsafe legacy numeric membership ID without converting the rounded number to a string', async () => {
  const client = api('[{"id":9007199254740993,"tenantId":11,"userId":13}]')
  await expect(client.memberships()).rejects.toThrow('失真')
})

it('retains the original decimal string across the response boundary', async () => {
  const client = api('[{"id":"9223372036854775807","tenantId":"9007199254740993","userId":"13"}]')
  expect((await client.memberships())[0]).toMatchObject({ id: '9223372036854775807', tenantId: '9007199254740993' })
})
