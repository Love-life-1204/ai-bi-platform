import { ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { useClientPagination } from './useClientPagination'

describe('useClientPagination', () => {
  it('only exposes the current bounded page', () => {
    const rows = ref(Array.from({ length: 251 }, (_, index) => index + 1))
    const pagination = useClientPagination(rows, 50)
    expect(pagination.pageRows.value).toHaveLength(50)
    pagination.page.value = 6
    expect(pagination.pageRows.value).toEqual([251])
  })
})

