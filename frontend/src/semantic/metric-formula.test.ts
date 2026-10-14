import { describe, expect, it } from 'vitest'
import { buildControlledFormula, trialMetricFormula, validateFormulaDraft, type MetricFormulaDraft } from './metric-formula'
import type { DatasetField } from './types'

const fields: DatasetField[] = [
  field('status', '订单状态', 'STRING', 'DIMENSION'),
  field('paid', '实付金额', 'DECIMAL', 'MEASURE'),
  field('refunded', '退款金额', 'DECIMAL', 'MEASURE'),
  field('orders', '订单数', 'LONG', 'MEASURE'),
  field('currency', '币种', 'STRING', 'DIMENSION'),
]

describe('controlled metric formula and trial', () => {
  it('excludes cancelled orders and subtracts confirmed refunds', () => {
    const formula = buildControlledFormula(draft(), fields)
    const result = trialMetricFormula(formula, [
      { status: 'PAID', paid: 100, refunded: 0, currency: 'CNY' },
      { status: 'PAID', paid: 200, refunded: 50, currency: 'CNY' },
      { status: 'CANCELLED', paid: 300, refunded: 0, currency: 'CNY' },
    ], config(), draft())
    expect(result.value).toBe('250.00')
    expect(result.matchingRows).toBe(2)
  })

  it('reports cross currency instead of directly adding it', () => {
    const result = trialMetricFormula(buildControlledFormula(draft(), fields), [
      { status: 'PAID', paid: 100, refunded: 0, currency: 'CNY' },
      { status: 'PAID', paid: 10, refunded: 0, currency: 'USD' },
    ], config(), draft())
    expect(result.status).toBe('CROSS_CURRENCY')
    expect(result.value).toBeUndefined()
  })

  it('skips missing values and reports affected rows', () => {
    const result = trialMetricFormula(buildControlledFormula(draft(), fields), [
      { status: 'PAID', paid: 100, refunded: null, currency: 'CNY' },
    ], config(), draft())
    expect(result.value).toBe('100.00')
    expect(result.missingValues).toBe(1)
  })

  it('returns undefined for a protected zero denominator', () => {
    const ratio = { ...draft(), combine: 'DIV' as const, secondaryFieldKey: 'orders' }
    const result = trialMetricFormula(buildControlledFormula(ratio, fields), [
      { status: 'PAID', paid: 100, orders: 0, currency: 'CNY' },
    ], config(), ratio)
    expect(result.status).toBe('UNDEFINED')
  })

  it('rejects a text field used by numeric aggregation', () => {
    expect(validateFormulaDraft({ ...draft(), fieldKey: 'status' }, fields))
      .toContainEqual(expect.objectContaining({ code: 'NUMERIC_FIELD_REQUIRED' }))
  })
})

function draft(): MetricFormulaDraft {
  return { aggregation: 'SUM', fieldKey: 'paid', combine: 'SUB', secondaryAggregation: 'SUM',
    secondaryFieldKey: 'refunded', filterEnabled: true, filterFieldKey: 'status', filterOperator: 'NE',
    filterValue: 'CANCELLED', protectZero: true }
}
function config() { return { precisionScale: 2, currencyMode: 'SINGLE' as const, currencyField: 'currency', targetCurrency: 'CNY' } }
function field(stableFieldKey: string, businessName: string, dataType: string,
    role: DatasetField['role']): DatasetField {
  return { id: String(stableFieldKey.length), stableFieldKey, physicalName: stableFieldKey, businessName,
    dataType, role, sensitivity: 'INTERNAL' }
}
