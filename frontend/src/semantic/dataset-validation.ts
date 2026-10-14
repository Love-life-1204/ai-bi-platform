import type { Id } from '../api/resource-id'
import type { DatasetRelation, FieldRole, Sensitivity, ValidationIssue } from './types'

export const DATASET_FIELD_TYPES = ['STRING', 'BOOLEAN', 'INTEGER', 'LONG', 'DECIMAL', 'DATE', 'DATETIME', 'TIMESTAMP'] as const
export type DatasetFieldType = typeof DATASET_FIELD_TYPES[number]

export interface EditableDatasetField {
  selected: boolean
  stableFieldKey: string
  sourceColumnId?: Id
  physicalName: string
  businessName: string
  dataType: DatasetFieldType
  role: FieldRole
  sensitivity: Sensitivity
  displayFormat: string
}

export interface EditableDatasetRelation extends Omit<DatasetRelation, 'id'> {}

export function normalizeDatasetType(value: string): DatasetFieldType {
  const type = value.toUpperCase()
  if (/BOOL|BIT/.test(type)) return 'BOOLEAN'
  if (/TIMESTAMP/.test(type)) return 'TIMESTAMP'
  if (/DATETIME|TIME/.test(type)) return 'DATETIME'
  if (/^DATE$/.test(type)) return 'DATE'
  if (/DECIMAL|NUMERIC|DOUBLE|FLOAT|MONEY/.test(type)) return 'DECIMAL'
  if (/BIGINT|LONG/.test(type)) return 'LONG'
  if (/INT|SMALLINT|TINYINT/.test(type)) return 'INTEGER'
  return 'STRING'
}

export function defaultDisplayFormat(type: DatasetFieldType): string {
  if (type === 'DATE') return 'yyyy-MM-dd'
  if (type === 'DATETIME' || type === 'TIMESTAMP') return 'yyyy-MM-dd HH:mm:ss'
  if (type === 'DECIMAL') return '#,##0.00'
  return ''
}

export function localDatasetIssues(fields: EditableDatasetField[], relations: EditableDatasetRelation[]): ValidationIssue[] {
  const issues: ValidationIssue[] = []
  const selected = fields.filter(field => field.selected)
  const keys = new Set<string>()
  const names = new Set<string>()
  selected.forEach((field, index) => {
    if (!field.stableFieldKey.trim()) issues.push(issue('FIELD_KEY_REQUIRED', `fields[${index}].stableFieldKey`, '稳定字段键不能为空'))
    if (!field.businessName.trim()) issues.push(issue('BUSINESS_NAME_REQUIRED', `fields[${index}].businessName`, '业务名称不能为空'))
    if (keys.has(field.stableFieldKey.trim())) issues.push(issue('DUPLICATE_FIELD_KEY', `fields[${index}].stableFieldKey`, '稳定字段键不能重复'))
    if (names.has(field.businessName.trim())) issues.push(issue('DUPLICATE_BUSINESS_NAME', `fields[${index}].businessName`, '业务名称不能重复'))
    keys.add(field.stableFieldKey.trim())
    names.add(field.businessName.trim())
  })
  const relationCodes = new Set<string>()
  relations.forEach((relation, index) => {
    const path = `relations[${index}]`
    if (relationCodes.has(relation.code.trim())) issues.push(issue('DUPLICATE_RELATION_CODE', `${path}.code`, '关联编码不能重复'))
    relationCodes.add(relation.code.trim())
    if (!keys.has(relation.leftFieldKey)) issues.push(issue('LEFT_KEY_NOT_FOUND', `${path}.leftFieldKey`, '左关联字段不存在'))
    if (!relation.approved) issues.push(issue('RELATION_NOT_APPROVED', `${path}.approved`, '关联发布前必须确认批准'))
    if (relation.cardinality === 'MANY_TO_MANY') issues.push(issue('MANY_TO_MANY_NOT_ALLOWED', `${path}.cardinality`, '多对多关联可能重复累计度量，不能发布'))
    const uniqueRequired = relation.cardinality === 'ONE_TO_ONE' || relation.cardinality === 'MANY_TO_ONE'
    if (uniqueRequired && !relation.rightKeyUnique && !relation.deduplicationRule) {
      issues.push(issue('DUPLICATE_DIMENSION_KEY', `${path}.rightFieldKey`, '右侧维度键必须唯一，或选择去重规则'))
    }
  })
  return issues
}

export function issueSection(path: string): 1 | 2 {
  return path.startsWith('relations[') ? 2 : 1
}

function issue(code: string, path: string, message: string): ValidationIssue {
  return { code, path, message }
}
