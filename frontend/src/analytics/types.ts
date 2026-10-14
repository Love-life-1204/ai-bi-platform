import type { Id } from '../api/resource-id'
export interface TimePoint {
  timestamp: string
  value: number
}

export interface ForecastRequest {
  metricVersionId: Id
  points: TimePoint[]
  horizon: number
  seasonLength: number
  trainingSize: number
  validationSize: number
  confidenceLevel: number
}

export interface BacktestMetrics {
  sampleCount: number
  mae: number
  rmse: number
  mape?: number
  intervalCoverage: number
}

export interface ForecastPoint {
  timestamp: string
  predicted: number
  lower: number
  upper: number
  confidenceLevel: number
}

export interface ForecastResult {
  metricVersionId: Id
  model: string
  backtest: BacktestMetrics
  forecast: ForecastPoint[]
}
