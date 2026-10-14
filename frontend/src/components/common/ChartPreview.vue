<template>
  <div ref="root" class="chart-preview-host" />
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ChartView } from '../../dashboard/chart-view'
import type { ChartSpec } from '../../dashboard/types'
import type { QueryResult } from '../../query/types'

const props = withDefaults(defineProps<{
  spec: ChartSpec
  result: QueryResult | undefined
  loading?: boolean
  error?: string
}>(), { loading: false, error: '' })
const emit = defineEmits<{
  retry: []
  filter: [field: string, value: unknown]
  drill: [field: string, value: unknown, row: Record<string, unknown>]
}>()
const root = ref<HTMLElement>()
let view: ChartView | undefined

async function render(): Promise<void> {
  await nextTick()
  if (!root.value) return
  view ??= new ChartView(root.value, {
    onRetry: () => emit('retry'),
    onFilter: (field, value) => emit('filter', field, value),
    onDrill: (field, value, row) => emit('drill', field, value, row),
  })
  if (props.loading) view.loading()
  else if (props.error) view.error(props.error, true)
  else if (props.result) view.render(props.spec, props.result)
  else view.error('完成数据集、指标和字段配置后将自动生成预览')
}

watch(() => [props.spec, props.result, props.loading, props.error], () => void render(), { deep: true, immediate: true })
onBeforeUnmount(() => { view?.destroy(); view = undefined })
</script>
