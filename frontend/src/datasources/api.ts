import type { Id } from '../api/resource-id'
import { ApiClient } from '../api/client'
import type {
  ConnectionTestResult,
  Datasource,
  DatasourceInput,
  DatasourceUpdateInput,
  DatasourceDependency,
  FileData,
  FilePreviewData,
  MetadataSnapshot,
} from './types'

export interface RequestClient {
  request<T>(path: string, options?: { method?: string; body?: unknown }): Promise<T>
}

export class DatasourceApi {
  constructor(private readonly client: RequestClient | ApiClient) {}

  list(workspaceId?: Id): Promise<Datasource[]> {
    const query = workspaceId === undefined ? '' : `?workspaceId=${encodeURIComponent(workspaceId)}`
    return this.client.request<Datasource[]>(`/datasources${query}`)
  }

  create(input: DatasourceInput): Promise<Datasource> {
    return this.client.request<Datasource>('/datasources', { method: 'POST', body: input })
  }

  update(id: Id, input: DatasourceUpdateInput): Promise<Datasource> {
    return this.client.request<Datasource>(`/datasources/${id}`, { method: 'PUT', body: input })
  }

  deactivate(datasource: Datasource): Promise<Datasource> {
    return this.client.request<Datasource>(
      `/datasources/${datasource.id}/deactivate?lockVersion=${datasource.lockVersion}`,
      { method: 'POST' },
    )
  }

  testDraft(input: DatasourceInput): Promise<ConnectionTestResult> {
    return this.client.request<ConnectionTestResult>('/datasources/connection-tests', {
      method: 'POST',
      body: { workspaceId: input.workspaceId, type: input.type, endpoint: input.endpoint, credential: input.credential },
    })
  }

  testSaved(id: Id): Promise<ConnectionTestResult> {
    return this.client.request<ConnectionTestResult>(`/datasources/${id}/connection-test`, {
      method: 'POST',
    })
  }

  collectMetadata(id: Id): Promise<MetadataSnapshot> {
    return this.client.request<MetadataSnapshot>(`/datasources/${id}/metadata/collect`, { method: 'POST' })
  }

  metadataSnapshots(id: Id): Promise<MetadataSnapshot[]> {
    return this.client.request<MetadataSnapshot[]>(`/datasources/${id}/metadata/snapshots`)
  }

  dependencies(id: Id): Promise<DatasourceDependency> {
    return this.client.request<DatasourceDependency>(`/datasources/${id}/dependencies`)
  }

  delete(datasource: Datasource): Promise<void> {
    return this.client.request<void>(
      `/datasources/${datasource.id}?lockVersion=${datasource.lockVersion}`,
      { method: 'DELETE' },
    )
  }

  uploadFile(file: File, workspaceId: Id): Promise<FileData> {
    const body = new FormData()
    body.append('file', file)
    body.append('workspaceId', String(workspaceId))
    return this.client.request<FileData>('/files/uploads', { method: 'POST', body })
  }

  previewFile(fileId: Id): Promise<FilePreviewData> {
    return this.client.request<FilePreviewData>(`/files/${fileId}/preview`)
  }
}
