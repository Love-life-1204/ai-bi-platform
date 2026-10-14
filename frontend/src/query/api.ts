import { ApiClient } from '../api/client'
import type { QueryPlan, QueryResult } from './types'

export class QueryApi {
  constructor(private readonly client: ApiClient) {}

  execute(plan: QueryPlan, signal?: AbortSignal, silent = false): Promise<QueryResult> {
    return this.client.request<QueryResult>('/queries/plans/execute', {
      method: 'POST', ...(signal ? { signal } : {}),
      body: plan,
      timeoutMs: 35_000,
      silent,
    })
  }
}
