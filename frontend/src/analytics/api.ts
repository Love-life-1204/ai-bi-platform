import type { ApiClient } from '../api/client'
import type { ForecastRequest, ForecastResult } from './types'

export class AnalyticsApi {
  constructor(private readonly client: ApiClient) {}

  forecast(request: ForecastRequest): Promise<ForecastResult> {
    return this.client.request('/analytics/forecast', {
      method: 'POST',
      body: request,
      timeoutMs: 20_000,
    })
  }
}
