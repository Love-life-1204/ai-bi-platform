<template>
  <div class="common-table-shell">
    <el-table
      :data="rows"
      v-loading="loading"
      :row-key="rowKey"
      stripe
      class="common-table"
      empty-text="暂无数据"
      @selection-change="handleSelectionChange"
    >
      <el-table-column v-if="selectable" type="selection" width="48" />
      <el-table-column
        v-for="column in columns"
        :key="column.key"
        :prop="column.key"
        :label="column.label"
        :min-width="column.minWidth"
        :width="column.width"
        :align="column.align ?? 'left'"
        :show-overflow-tooltip="column.overflowTooltip ?? true"
      >
        <template #default="scope">
          <slot :name="`cell-${column.key}`" :row="scope.row" :value="scope.row[column.key]">
            {{ formatValue(scope.row[column.key]) }}
          </slot>
        </template>
      </el-table-column>
      <el-table-column v-if="$slots.actions" label="操作" align="right" fixed="right" :width="actionsWidth">
        <template #default="scope"><slot name="actions" :row="scope.row" /></template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
export interface TableColumn {
  key: string
  label: string
  width?: number | string
  minWidth?: number | string
  align?: 'left' | 'center' | 'right'
  overflowTooltip?: boolean
}

withDefaults(defineProps<{
  rows: Record<string, unknown>[]
  columns: TableColumn[]
  loading?: boolean
  selectable?: boolean
  rowKey?: string
  actionsWidth?: number
}>(), { loading: false, selectable: false, rowKey: 'id', actionsWidth: 160 })

const emit = defineEmits<{ 'selection-change': [rows: Record<string, unknown>[]] }>()

function handleSelectionChange(values: Record<string, unknown>[]): void {
  emit('selection-change', values)
}

function formatValue(value: unknown): string {
  if (value === undefined || value === null || value === '') return '—'
  return String(value)
}
</script>
