import type { Id } from '../api/resource-id'
export interface FieldMapping {
  sourceField: string
  targetField: string
  targetType: 'STRING' | 'LONG' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME'
  datePattern?: string
  required: boolean
}

export type TaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED' | 'CANCELLED' | 'TIMEOUT'

export interface ImportJob {
  id: Id
  datasetId: Id
  objectFileId: Id
  status: TaskStatus
  totalRows: number
  validRows: number
  errorRows: number
  errorFileId?: Id
  errorSummary?: string
  createdAt: string
  updatedAt: string
}

export interface CreateImportInput {
  workspaceId: Id
  datasetId: Id
  requesterMemberId: Id
  objectFileId: Id
  mappings: FieldMapping[]
  strictMode: boolean
}

export interface CreateSyncJobInput {
  datasourceId: Id
  datasetId: Id
  jobCode: string
  syncMode: 'FULL' | 'INCREMENTAL'
  sourceSchema: string
  sourceTable: string
  primaryKeyField: string
  updatedAtField?: string
  amountField?: string
  mappings: FieldMapping[]
  batchSize: number
  scheduleExpression?: string
  timezone: string
  enabled: boolean
}

export interface UpdateSyncJobInput extends Omit<CreateSyncJobInput, 'datasourceId' | 'datasetId' | 'jobCode' | 'enabled'> {
  lockVersion: number
}

export interface SyncJob {
  id: Id
  datasourceId: Id
  datasetId: Id
  jobCode: string
  syncMode: 'FULL' | 'INCREMENTAL'
  sourceObject: string
  primaryKeyField: string
  updatedAtField?: string
  amountField?: string
  mappings: FieldMapping[]
  batchSize: number
  scheduleExpression?: string
  timezone: string
  status: 'ENABLED' | 'DISABLED'
  watermarkTime?: string
  watermarkPrimaryKey?: string
  lockVersion: number
  createdAt: string
  updatedAt: string
}

export interface SyncBatch {
  id: Id
  generationId: Id
  publishSeq: number
  status: string
  expectedRows: number
  receivedRows: number
  validRows: number
  rejectedRows: number
  deduplicatedRows: number
  sourceAmount: string
  targetAmount: string
  sourceBusinessKeys: number
  targetBusinessKeys: number
  qualityStatus: string
  errorReport?: string
  publishedAt?: string
}

export interface SyncRun {
  id: Id
  syncJobId: Id
  attemptNo: number
  status: TaskStatus
  leaseOwner?: string
  leaseUntil?: string
  fencingToken: number
  errorCode?: string
  errorSummary?: string
  retryCount: number
  deadLetter: boolean
  batch?: SyncBatch
  startedAt?: string
  finishedAt?: string
  createdAt: string
}

export interface TaskAccepted {
  taskId: Id
  status: string
}
