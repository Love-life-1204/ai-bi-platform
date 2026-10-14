import { describe, expect, it } from 'vitest'
import { defaultDisplayFormat, localDatasetIssues, normalizeDatasetType } from './dataset-validation'

describe('dataset draft validation', () => {
  it('normalizes physical types and assigns presentation formats', () => {
    expect(normalizeDatasetType('varchar(255)')).toBe('STRING')
    expect(normalizeDatasetType('numeric(18,2)')).toBe('DECIMAL')
    expect(normalizeDatasetType('timestamp')).toBe('TIMESTAMP')
    expect(defaultDisplayFormat('DECIMAL')).toBe('#,##0.00')
  })

  it('locates duplicate fields and duplicate relation codes', () => {
    const fields = [
      { selected: true, stableFieldKey: 'customer_id', physicalName: 'customer_id', businessName: '客户', dataType: 'STRING' as const, role: 'DIMENSION' as const, sensitivity: 'INTERNAL' as const, displayFormat: '' },
      { selected: true, stableFieldKey: 'customer_id', physicalName: 'customer_name', businessName: '客户', dataType: 'STRING' as const, role: 'DIMENSION' as const, sensitivity: 'INTERNAL' as const, displayFormat: '' },
    ]
    const relations = [
      { code: 'customer', relatedVersionId: '10', leftFieldKey: 'customer_id', rightFieldKey: 'id', cardinality: 'MANY_TO_ONE' as const, approved: true, rightKeyUnique: true },
      { code: 'customer', relatedVersionId: '11', leftFieldKey: 'customer_id', rightFieldKey: 'id', cardinality: 'ONE_TO_ONE' as const, approved: true, rightKeyUnique: true },
    ]
    expect(localDatasetIssues(fields, relations).map(issue => issue.code)).toEqual(expect.arrayContaining([
      'DUPLICATE_FIELD_KEY', 'DUPLICATE_BUSINESS_NAME', 'DUPLICATE_RELATION_CODE',
    ]))
  })

  it('warns about unapproved, many-to-many and non-unique dimension joins', () => {
    const fields = [{ selected: true, stableFieldKey: 'customer_id', physicalName: 'customer_id', businessName: '客户', dataType: 'STRING' as const, role: 'DIMENSION' as const, sensitivity: 'INTERNAL' as const, displayFormat: '' }]
    const relations = [{ code: 'customer', relatedVersionId: '10', leftFieldKey: 'customer_id', rightFieldKey: 'id', cardinality: 'MANY_TO_MANY' as const, approved: false, rightKeyUnique: false }]
    expect(localDatasetIssues(fields, relations).map(issue => issue.code)).toEqual(expect.arrayContaining([
      'RELATION_NOT_APPROVED', 'MANY_TO_MANY_NOT_ALLOWED',
    ]))
  })
})
