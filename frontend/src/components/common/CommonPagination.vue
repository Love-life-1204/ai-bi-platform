<template>
  <div class="common-pagination">
    <span class="pagination-total">共 {{ total }} 条</span>
    <el-pagination
      :current-page="page"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :total="total"
      :layout="layout"
      background
      @update:current-page="updatePage"
      @update:page-size="updatePageSize"
    />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  page: number
  pageSize: number
  total: number
  pageSizes?: number[]
  layout?: string
}>(), { pageSizes: () => [10, 20, 50, 100], layout: 'sizes, prev, pager, next, jumper' })

const emit = defineEmits<{ 'update:page': [page: number]; 'update:page-size': [size: number] }>()

function updatePage(value: number): void { emit('update:page', value) }
function updatePageSize(value: number): void { emit('update:page-size', value) }
</script>
