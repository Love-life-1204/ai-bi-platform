import { computed, ref, watch, type Ref } from 'vue'

export function useClientPagination<T>(rows: Ref<readonly T[]>, initialPageSize = 50) {
  const page = ref(1)
  const pageSize = ref(initialPageSize)
  const total = computed(() => rows.value.length)
  const pageRows = computed(() => {
    const start = (page.value - 1) * pageSize.value
    return rows.value.slice(start, start + pageSize.value)
  })
  watch([total, pageSize], () => {
    const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (page.value > lastPage) page.value = lastPage
  })
  watch(pageSize, () => { page.value = 1 })
  return { page, pageSize, total, pageRows }
}

