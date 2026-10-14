<template>
  <UnifiedState v-if="error" state="error" :description="error" retryable @retry="load" />
  <section v-else ref="root" class="legacy-page" />
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import UnifiedState from '../../components/common/UnifiedState.vue'
import { dashboardApi } from '../../api/runtime'
import { useAppStore } from '../../stores/app'

const app = useAppStore()
const root = ref<HTMLElement>()
const error = ref('')
let page: { load: () => Promise<void>; destroy: () => void } | undefined

async function load(): Promise<void> {
  error.value = ''
  await nextTick()
  if (!root.value) return
  const { DashboardPage } = await import('../../dashboard/dashboard-page')
  page?.destroy()
  page = new DashboardPage(root.value, dashboardApi, app.workspaceId)
  await page.load().catch(showError)
}

function showError(cause: unknown): void {
  error.value = cause instanceof Error ? cause.message : '看板页面加载失败'
}

onMounted(() => void load())
onUnmounted(() => { page?.destroy(); page = undefined })
</script>
