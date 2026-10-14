import type { Id } from '../api/resource-id'
import type { DatasetField, ValidationIssue } from './types'

export type Aggregation = 'SUM' | 'COUNT' | 'COUNT_DISTINCT' | 'AVG' | 'MIN' | 'MAX'
export type CombineOperator = 'NONE' | 'ADD' | 'SUB' | 'DIV'
export type CompareOperator = 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE'
export type CurrencyMode = 'SINGLE' | 'FIXED' | 'CONVERT'

export interface FormulaNode {
  op: string
  field?: string
  value?: unknown
  args?: FormulaNode[]
  metricVersionId?: Id
}

export interface MetricFormulaDraft {
  aggregation: Aggregation
  fieldKey: string
  combine: CombineOperator
  secondaryAggregation: Aggregation
  secondaryFieldKey: string
  filterEnabled: boolean
  filterFieldKey: string
  filterOperator: CompareOperator
  filterValue: string
  protectZero: boolean
}

export interface TrialConfiguration {
  precisionScale: number
  currencyMode: CurrencyMode
  currencyField: string
  targetCurrency: string
}

export interface MetricTrialResult {
  status: 'DEFINED' | 'UNDEFINED' | 'EMPTY' | 'CROSS_CURRENCY'
  value?: string
  currency?: string
  rowCount: number
  matchingRows: number
  missingValues: number
  notes: string[]
}

export function buildControlledFormula(draft: MetricFormulaDraft, fields: DatasetField[]): FormulaNode {
  const primary = aggregationNode(draft.aggregation, draft.fieldKey, draft, fields)
  if (draft.combine === 'NONE') return primary
  let secondary = aggregationNode(draft.secondaryAggregation, draft.secondaryFieldKey, draft, fields)
  if (draft.combine === 'DIV' && draft.protectZero) {
    secondary = { op: 'NULLIF', args: [secondary, { op: 'LITERAL', value: 0 }] }
  }
  return { op: draft.combine, args: [primary, secondary] }
}

export function validateFormulaDraft(draft: MetricFormulaDraft, fields: DatasetField[]): ValidationIssue[] {
  const issues: ValidationIssue[] = []
  const keys = new Set(fields.map(field => field.stableFieldKey))
  if (draft.aggregation !== 'COUNT' && !keys.has(draft.fieldKey)) {
    issues.push(issue('FORMULA_FIELD_REQUIRED', 'formula.primary.field', '请选择主计算字段'))
  }
  if (draft.combine !== 'NONE' && draft.secondaryAggregation !== 'COUNT'
      && !keys.has(draft.secondaryFieldKey)) {
    issues.push(issue('SECONDARY_FIELD_REQUIRED', 'formula.secondary.field', '请选择第二计算字段'))
  }
  validateOperand(draft.aggregation, draft.fieldKey, 'formula.primary.field', fields, issues)
  if (draft.combine !== 'NONE') {
    validateOperand(draft.secondaryAggregation, draft.secondaryFieldKey,
      'formula.secondary.field', fields, issues)
  }
  if (draft.filterEnabled) {
    if (draft.aggregation === 'COUNT' || (draft.combine !== 'NONE' && draft.secondaryAggregation === 'COUNT')) {
      issues.push(issue('FILTERED_COUNT_UNSUPPORTED', 'formula.filter', '受控 COUNT 暂不支持行条件，请改用其他聚合'))
    }
    if (!keys.has(draft.filterFieldKey)) {
      issues.push(issue('FILTER_FIELD_REQUIRED', 'formula.filter.field', '请选择条件字段'))
    }
    if (!draft.filterValue.trim()) {
      issues.push(issue('FILTER_VALUE_REQUIRED', 'formula.filter.value', '请填写条件值'))
    }
  }
  return issues
}

function validateOperand(aggregation: Aggregation, fieldKey: string, path: string,
    fields: DatasetField[], issues: ValidationIssue[]): void {
  if (aggregation === 'COUNT' || !fieldKey) return
  const field = fields.find(item => item.stableFieldKey === fieldKey)
  if (field && ['SUM', 'AVG'].includes(aggregation)
      && !['INTEGER', 'LONG', 'DECIMAL'].includes(field.dataType)) {
    issues.push(issue('NUMERIC_FIELD_REQUIRED', path, `${aggregation} 只能使用数值字段`))
  }
}

export function formulaSummary(draft: MetricFormulaDraft, fields: DatasetField[]): string {
  const names = new Map(fields.map(field => [field.stableFieldKey, field.businessName]))
  const aggregate = (op: Aggregation, key: string) => op === 'COUNT' ? '记录数' : `${op}(${names.get(key) ?? key})`
  let summary = aggregate(draft.aggregation, draft.fieldKey)
  if (draft.combine !== 'NONE') {
    const symbol = { ADD: '+', SUB: '−', DIV: '÷' }[draft.combine]
    summary += ` ${symbol} ${aggregate(draft.secondaryAggregation, draft.secondaryFieldKey)}`
  }
  if (draft.filterEnabled) {
    summary += `；仅统计 ${names.get(draft.filterFieldKey) ?? draft.filterFieldKey} ${draft.filterOperator} ${draft.filterValue}`
  }
  if (draft.combine === 'DIV' && draft.protectZero) summary += '；分母为零返回未定义'
  return summary
}

export function trialMetricFormula(node: FormulaNode, rows: Record<string, unknown>[],
    config: TrialConfiguration, draft?: MetricFormulaDraft): MetricTrialResult {
  const matchingRows = draft?.filterEnabled ? rows.filter(row => filterMatches(draft, row)).length : rows.length
  const referenced = referencedFields(node)
  const missingValues = rows.filter(row => [...referenced].some(key => row[key] === null || row[key] === undefined)).length
  const notes: string[] = []
  if (!rows.length) return { status: 'EMPTY', rowCount: 0, matchingRows: 0, missingValues: 0, notes: ['权限预览没有可用于试算的样例行'] }

  const currencies = new Set(rows.map(row => String(row[config.currencyField] ?? '')).filter(Boolean))
  if (currencies.size > 1) {
    if (config.currencyMode !== 'CONVERT') {
      return { status: 'CROSS_CURRENCY', rowCount: rows.length, matchingRows, missingValues,
        notes: ['样例包含多个币种，禁止直接合计；请配置目标币种和已批准汇率来源'] }
    }
    return { status: 'CROSS_CURRENCY', rowCount: rows.length, matchingRows, missingValues,
      currency: config.targetCurrency, notes: ['跨币种试算需要后端批准汇率数据，当前页面不会生成假汇率结果'] }
  }

  const value = evaluateNode(node, rows)
  if (missingValues) notes.push(`${missingValues} 行包含缺失值；聚合函数按空值规则跳过`)
  if (value === null || value === undefined || !Number.isFinite(Number(value))) {
    notes.push('结果未定义，常见原因是分母为零或所有值均为空')
    return { status: 'UNDEFINED', rowCount: rows.length, matchingRows, missingValues, notes }
  }
  const currency = config.currencyMode === 'CONVERT' || config.currencyMode === 'FIXED'
    ? config.targetCurrency : [...currencies][0]
  return { status: 'DEFINED', value: Number(value).toFixed(config.precisionScale),
    ...(currency ? { currency } : {}), rowCount: rows.length, matchingRows, missingValues, notes }
}

function aggregationNode(op: Aggregation, fieldKey: string, draft: MetricFormulaDraft,
    fields: DatasetField[]): FormulaNode {
  if (op === 'COUNT') return { op: 'COUNT', args: [] }
  let value: FormulaNode = { op: 'FIELD', field: fieldKey }
  if (draft.filterEnabled) {
    const field = fields.find(item => item.stableFieldKey === draft.filterFieldKey)
    value = { op: 'CASE', args: [
      { op: draft.filterOperator, args: [
        { op: 'FIELD', field: draft.filterFieldKey },
        { op: 'LITERAL', value: typedLiteral(draft.filterValue, field?.dataType) },
      ] },
      value,
      { op: 'LITERAL', value: null },
    ] }
  }
  return { op, args: [value] }
}

function typedLiteral(value: string, dataType?: string): unknown {
  if (['INTEGER', 'LONG', 'DECIMAL'].includes(dataType ?? '') && value.trim() !== '') return Number(value)
  if (dataType === 'BOOLEAN') return value.toLowerCase() === 'true'
  return value
}

function evaluateNode(node: FormulaNode, rows: Record<string, unknown>[], row?: Record<string, unknown>): unknown {
  const args = node.args ?? []
  if (node.op === 'FIELD') return row?.[node.field ?? ''] ?? null
  if (node.op === 'LITERAL') return node.value
  if (node.op === 'COUNT') return rows.length
  if (['SUM', 'AVG', 'MIN', 'MAX', 'COUNT_DISTINCT'].includes(node.op)) {
    const values = rows.map(item => evaluateNode(args[0] ?? { op: 'LITERAL', value: null }, rows, item))
      .filter(value => value !== null && value !== undefined)
    if (node.op === 'COUNT_DISTINCT') return new Set(values.map(value => String(value))).size
    const numbers = values.map(numberValue).filter((value): value is number => value !== null)
    if (node.op === 'SUM') return numbers.reduce((total, value) => total + value, 0)
    if (!numbers.length) return null
    if (node.op === 'AVG') return numbers.reduce((total, value) => total + value, 0) / numbers.length
    return node.op === 'MIN' ? Math.min(...numbers) : Math.max(...numbers)
  }
  if (node.op === 'CASE') return truthy(evaluateNode(args[0] ?? { op: 'LITERAL' }, rows, row))
    ? evaluateNode(args[1] ?? { op: 'LITERAL' }, rows, row)
    : evaluateNode(args[2] ?? { op: 'LITERAL' }, rows, row)
  const left = evaluateNode(args[0] ?? { op: 'LITERAL' }, rows, row)
  const right = evaluateNode(args[1] ?? { op: 'LITERAL' }, rows, row)
  if (node.op === 'NULLIF') return equal(left, right) ? null : left
  if (['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE'].includes(node.op)) return compare(node.op, left, right)
  const leftNumber = numberValue(left)
  const rightNumber = numberValue(right)
  if (leftNumber === null || rightNumber === null) return null
  if (node.op === 'ADD') return leftNumber + rightNumber
  if (node.op === 'SUB') return leftNumber - rightNumber
  if (node.op === 'DIV') return rightNumber === 0 ? null : leftNumber / rightNumber
  return null
}

function referencedFields(node: FormulaNode, fields = new Set<string>()): Set<string> {
  if (node.op === 'FIELD' && node.field) fields.add(node.field)
  node.args?.forEach(child => referencedFields(child, fields))
  return fields
}

function filterMatches(draft: MetricFormulaDraft, row: Record<string, unknown>): boolean {
  return compare(draft.filterOperator, row[draft.filterFieldKey], draft.filterValue)
}

function compare(op: string, left: unknown, right: unknown): boolean {
  if (op === 'EQ') return equal(left, right)
  if (op === 'NE') return !equal(left, right)
  const a = numberValue(left)
  const b = numberValue(right)
  if (a === null || b === null) return false
  return op === 'GT' ? a > b : op === 'GTE' ? a >= b : op === 'LT' ? a < b : a <= b
}

function equal(left: unknown, right: unknown): boolean {
  return String(left ?? '') === String(right ?? '')
}

function truthy(value: unknown): boolean { return value === true || value === 1 || value === 'true' }
function numberValue(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}
function issue(code: string, path: string, message: string): ValidationIssue { return { code, path, message } }
