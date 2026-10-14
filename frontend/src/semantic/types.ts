import type { Id } from '../api/resource-id'
export type SemanticStatus = 'DRAFT' | 'VALIDATED' | 'PUBLISHED'
export type FieldRole = 'DIMENSION' | 'MEASURE'
export type Sensitivity = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED'

export interface DatasetField {
  id: Id
  stableFieldKey: string
  sourceColumnId?: Id
  physicalName: string
  businessName: string
  dataType: string
  role: FieldRole
  sensitivity: Sensitivity
  displayFormat?: string
}

export interface DatasetRelation {
  id: Id
  code: string
  relatedVersionId: Id
  leftFieldKey: string
  rightFieldKey: string
  cardinality: 'ONE_TO_ONE' | 'MANY_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_MANY'
  approved: boolean
  rightKeyUnique: boolean
  deduplicationRule?: string
}

export interface DatasetVersion {
  id: Id
  versionNo: number
  sourceObjectId?: Id
  fields: DatasetField[]
  relations: DatasetRelation[]
  previewRows: Record<string, unknown>[]
  definitionHash: string
  status: SemanticStatus
  publishedAt?: string
  lockVersion: number
}

export interface Dataset {
  id: Id
  workspaceId: Id
  code: string
  name: string
  status: SemanticStatus
  currentVersionId?: Id
  ownerMemberId: Id
  createdAt: string
  updatedAt: string
  lockVersion: number
}

export interface DatasetData { dataset: Dataset; draft?: DatasetVersion }

export interface DatasetInput {
  workspaceId: Id
  code: string
  name: string
  sourceObjectId?: Id
  fields: Omit<DatasetField, 'id'>[]
  relations: Omit<DatasetRelation, 'id'>[]
  previewRows: Record<string, unknown>[]
}

export interface DatasetUpdateInput extends Omit<DatasetInput, 'workspaceId' | 'code'> {
  lockVersion: number
}

export interface MetricVersion {
  id: Id
  versionNo: number
  datasetVersionId: Id
  formulaAst: unknown
  timeSpec: unknown
  currencySpec: unknown
  allowedDimensions: string[]
  precisionScale: number
  dependencyVersionIds: Id[]
  definitionHash: string
  status: SemanticStatus
  publishedAt?: string
  lockVersion: number
}

export interface Metric {
  id: Id
  workspaceId: Id
  code: string
  name: string
  unit: string
  status: SemanticStatus
  currentVersionId?: Id
  ownerMemberId: Id
  createdAt: string
  updatedAt: string
  lockVersion: number
}

export interface MetricData { metric: Metric; draft?: MetricVersion }

export interface MetricInput {
  workspaceId: Id
  code: string
  name: string
  unit: string
  datasetVersionId: Id
  formulaAst: unknown
  timeSpec: unknown
  currencySpec: unknown
  allowedDimensions: string[]
  precisionScale: number
  dependencyVersionIds: Id[]
}

export interface MetricUpdateInput extends Omit<MetricInput, 'workspaceId' | 'code'> {
  lockVersion: number
}

export interface ValidationIssue { code: string; path: string; message: string }
export interface ValidationResult {
  valid: boolean
  errors: ValidationIssue[]
  warnings: ValidationIssue[]
}

export interface PreviewData {
  datasetId: Id
  datasetVersionId: Id
  fields: DatasetField[]
  rows: Record<string, unknown>[]
  totalBeforePolicy: number
  visibleAfterPolicy: number
}
