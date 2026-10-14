import type { Id } from '../api/resource-id'
import type { RequestClient } from '../datasources/api'
import type {
  Dataset,
  DatasetData,
  DatasetInput,
  DatasetUpdateInput,
  DatasetVersion,
  Metric,
  MetricData,
  MetricInput,
  MetricUpdateInput,
  MetricVersion,
  PreviewData,
  ValidationResult,
} from './types'

export class SemanticApi {
  constructor(private readonly client: RequestClient) {}

  datasets(workspaceId: Id): Promise<Dataset[]> {
    return this.client.request<Dataset[]>(`/datasets?workspaceId=${encodeURIComponent(workspaceId)}`)
  }

  createDataset(input: DatasetInput): Promise<DatasetData> {
    return this.client.request<DatasetData>('/datasets', { method: 'POST', body: input })
  }

  dataset(id: Id): Promise<DatasetData> {
    return this.client.request<DatasetData>(`/datasets/${id}`)
  }

  updateDataset(id: Id, input: DatasetUpdateInput): Promise<DatasetData> {
    return this.client.request<DatasetData>(`/datasets/${id}`, { method: 'PATCH', body: input })
  }

  datasetVersions(id: Id): Promise<DatasetVersion[]> {
    return this.client.request<DatasetVersion[]>(`/datasets/${id}/versions`)
  }

  validateDataset(id: Id): Promise<ValidationResult> {
    return this.client.request<ValidationResult>(`/datasets/${id}/validate`, { method: 'POST' })
  }

  publishDataset(id: Id): Promise<DatasetVersion> {
    return this.client.request<DatasetVersion>(`/datasets/${id}/publish`, { method: 'POST' })
  }

  previewDataset(id: Id): Promise<PreviewData> {
    return this.client.request<PreviewData>(`/datasets/${id}/preview`)
  }

  metrics(workspaceId: Id): Promise<Metric[]> {
    return this.client.request<Metric[]>(`/metrics?workspaceId=${encodeURIComponent(workspaceId)}`)
  }

  createMetric(input: MetricInput): Promise<MetricData> {
    return this.client.request<MetricData>('/metrics', { method: 'POST', body: input })
  }

  metric(id: Id): Promise<MetricData> {
    return this.client.request<MetricData>(`/metrics/${id}`)
  }

  updateMetric(id: Id, input: MetricUpdateInput): Promise<MetricData> {
    return this.client.request<MetricData>(`/metrics/${id}`, { method: 'PATCH', body: input })
  }

  metricVersions(id: Id): Promise<MetricVersion[]> {
    return this.client.request<MetricVersion[]>(`/metrics/${id}/versions`)
  }

  validateMetric(id: Id): Promise<ValidationResult> {
    return this.client.request<ValidationResult>(`/metrics/${id}/validate`, { method: 'POST' })
  }

  publishMetric(id: Id): Promise<MetricVersion> {
    return this.client.request<MetricVersion>(`/metrics/${id}/publish`, { method: 'POST' })
  }
}
