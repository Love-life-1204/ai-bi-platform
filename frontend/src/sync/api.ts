import type { Id } from '../api/resource-id'
import { ApiClient } from '../api/client'
import type {
  CreateImportInput,
  CreateSyncJobInput,
  ImportJob,
  SyncJob,
  SyncRun,
  TaskAccepted,
  UpdateSyncJobInput,
} from './types'

export class SyncApi {
  constructor(private readonly client: ApiClient) {}

  jobs(): Promise<SyncJob[]> {
    return this.client.request<SyncJob[]>('/sync-jobs')
  }

  job(id: Id): Promise<SyncJob> {
    return this.client.request<SyncJob>(`/sync-jobs/${id}`)
  }

  createJob(input: CreateSyncJobInput): Promise<SyncJob> {
    return this.client.request<SyncJob>('/sync-jobs', { method: 'POST', body: input })
  }

  updateJob(id: Id, input: UpdateSyncJobInput): Promise<SyncJob> {
    return this.client.request<SyncJob>(`/sync-jobs/${id}`, { method: 'PATCH', body: input })
  }

  runs(jobId?: Id): Promise<SyncRun[]> {
    const query = jobId === undefined ? '' : `?jobId=${encodeURIComponent(jobId)}`
    return this.client.request<SyncRun[]>(`/sync-runs${query}`)
  }

  runDetail(id: Id): Promise<SyncRun> {
    return this.client.request<SyncRun>(`/sync-runs/${id}`)
  }

  run(jobId: Id): Promise<TaskAccepted> {
    return this.client.request<TaskAccepted>(`/sync-jobs/${jobId}/run`, {
      method: 'POST',
      idempotencyKey: globalThis.crypto.randomUUID(),
    })
  }

  setEnabled(job: SyncJob, enabled: boolean): Promise<SyncJob> {
    const action = enabled ? 'enable' : 'pause'
    return this.client.request<SyncJob>(
      `/sync-jobs/${job.id}/${action}?lockVersion=${job.lockVersion}`,
      { method: 'POST' },
    )
  }

  cancelRun(id: Id): Promise<SyncRun> {
    return this.client.request<SyncRun>(`/sync-runs/${id}/cancel`, { method: 'POST' })
  }

  compensate(run: SyncRun, reason: string): Promise<TaskAccepted> {
    return this.client.request<TaskAccepted>(`/sync-runs/${run.id}/compensate`, {
      method: 'POST',
      body: { reason, operator: 'current-user' },
    })
  }


  imports(): Promise<ImportJob[]> {
    return this.client.request<ImportJob[]>('/imports')
  }

  importStatus(id: Id): Promise<ImportJob> {
    return this.client.request<ImportJob>(`/imports/${id}`)
  }

  createImport(input: CreateImportInput): Promise<ImportJob> {
    return this.client.request<ImportJob>('/imports', {
      method: 'POST',
      body: input,
      idempotencyKey: globalThis.crypto.randomUUID(),
    })
  }

  cancelImport(id: Id): Promise<ImportJob> {
    return this.client.request<ImportJob>(`/imports/${id}/cancel`, { method: 'POST' })
  }
}
