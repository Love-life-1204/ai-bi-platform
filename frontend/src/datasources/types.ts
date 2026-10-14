import type { Id } from '../api/resource-id'
export type DatasourceType = 'MYSQL' | 'POSTGRESQL'
export type DatasourceStatus = 'ACTIVE' | 'DISABLED'

export interface DatasourceEndpoint {
  host: string
  port: number
  database: string
  tls: boolean
}

export interface DatasourceCredential {
  username: string
  password: string
}

export interface DatasourceInput {
  workspaceId: Id
  code: string
  name: string
  type: DatasourceType
  endpoint: DatasourceEndpoint
  credential: DatasourceCredential
}

export interface DatasourceUpdateInput {
  name: string
  endpoint: DatasourceEndpoint
  credential?: DatasourceCredential
  lockVersion: number
}

export interface Datasource {
  id: Id
  workspaceId: Id
  code: string
  name: string
  type: DatasourceType
  endpoint: DatasourceEndpoint
  hasCredential: boolean
  ownerMemberId?: Id
  lastTestStatus?: string
  lastTestAt?: string
  status: DatasourceStatus
  createdAt: string
  updatedAt: string
  lockVersion: number
}

export interface ConnectionTestResult {
  status: 'SUCCESS'
  latencyMs: number
  databaseVersion: string
  message: string
}

export interface SourceColumn {
  id?: Id
  name: string
  stableKey: string
  ordinalPosition: number
  dataType: string
  nullable: boolean
  primaryKey: boolean
  comment?: string
}

export interface SourceObject {
  id?: Id
  schema: string
  name: string
  type: string
  comment?: string
  schemaHash: string
  columns: SourceColumn[]
}

export interface MetadataSnapshot {
  id: Id
  datasourceId: Id
  version: number
  schemaHash: string
  objects: SourceObject[]
  collectedAt: string
}

export interface DatasourceDependency {
  datasourceId: Id
  syncJobs: Array<{ id: Id; jobCode: string; status: string }>
}

export interface FileData {
  workspaceId: Id
  ownerMemberId: Id
  id: Id
  fileName: string
  contentType: string
  sizeBytes: number
  sha256: string
  status: string
  createdAt: string
}

export interface FileColumnPreview {
  name: string
  inferredType: string
  nullable: boolean
  datePatterns: string[]
  samples: string[]
}

export interface FilePreviewData {
  fileId: Id
  sheetName: string
  sampledRows: number
  columns: FileColumnPreview[]
  rows: Record<string, string>[]
  truncated: boolean
}

export type ConnectionTestState =
  | { kind: 'idle'; message: string }
  | { kind: 'testing'; message: string }
  | { kind: 'success'; message: string; latencyMs: number }
  | { kind: 'auth-failed' | 'timeout' | 'permission-denied' | 'target-blocked' | 'forbidden' | 'unavailable' | 'failed'; message: string }
