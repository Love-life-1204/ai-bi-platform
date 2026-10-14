<template>
  <div class="workspace-home">
    <section class="workspace-hero">
      <div>
        <p class="eyebrow">{{ currentTenant }} · {{ currentWorkspace }}</p>
        <h2>{{ greeting }}，{{ app.userName }}</h2>
        <p>这里仅展示当前企业与工作空间授权范围内的经营数据。</p>
      </div>
      <div class="workspace-hero-actions">
        <el-button @click="router.push('/sync')">查看同步任务</el-button>
        <el-button type="primary" @click="router.push('/ai')">发起 AI 分析</el-button>
      </div>
    </section>

    <UnifiedState v-if="loading" state="loading" description="正在加载当前工作空间的业务摘要。" />
    <UnifiedState v-else-if="fatalError" state="error" :description="fatalError" retryable @retry="load" />
    <template v-else>
      <el-alert v-if="partialErrors.length" class="workspace-partial" type="warning" show-icon :closable="false">
        <template #title>部分区域暂时不可用：{{ partialErrors.join('；') }}</template>
      </el-alert>

      <section class="metric-grid" aria-label="授权指标摘要">
        <article v-for="metric in metricCards" :key="metric.key" class="metric-card">
          <div class="metric-icon" :class="`tone-${metric.tone}`"><el-icon><component :is="metric.icon" /></el-icon></div>
          <div>
            <span>{{ metric.label }}</span>
            <strong>{{ metric.display }}</strong>
            <small>{{ metric.note }}</small>
          </div>
        </article>
      </section>

      <div class="workspace-grid">
        <section class="workbench-card">
          <header><div><span class="eyebrow">RECENT</span><h3>最近看板</h3></div><el-button link type="primary" @click="router.push('/dashboards')">全部看板</el-button></header>
          <div v-if="dashboards.length" class="dashboard-list">
            <button v-for="dashboard in dashboards" :key="dashboard.id" type="button" @click="openDashboard(dashboard.id)">
              <span class="dashboard-badge"><el-icon><DataAnalysis /></el-icon></span>
              <span><strong>{{ dashboard.name }}</strong><small>{{ dashboard.code }} · {{ statusText(dashboard.status) }}</small></span>
              <el-tag :type="dashboard.status === 'PUBLISHED' ? 'success' : 'info'" size="small" effect="light">{{ statusText(dashboard.status) }}</el-tag>
            </button>
          </div>
          <UnifiedState v-else state="empty" title="暂无可访问看板" description="当前工作空间还没有看板，或当前成员尚未获得访问权限。" action-text="创建看板" @action="router.push('/dashboards')" />
        </section>

        <section class="workbench-card">
          <header><div><span class="eyebrow">SYNC STATUS</span><h3>数据同步任务</h3></div><el-button link type="primary" @click="router.push('/sync')">任务管理</el-button></header>
          <div v-if="syncRows.length" class="task-list">
            <div v-for="task in syncRows" :key="task.id" class="task-row">
              <span class="task-status-dot" :class="task.tone" />
              <div><strong>{{ task.name }}</strong><small>{{ task.source }} · {{ task.updatedAt }}</small></div>
              <el-tag :type="task.tagType" size="small" effect="light">{{ task.status }}</el-tag>
            </div>
          </div>
          <UnifiedState v-else state="empty" title="暂无同步任务" description="配置数据源与同步任务后，运行状态会显示在这里。" action-text="前往配置" @action="router.push('/sync')" />
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { readStoredIds } from '../api/resource-id'
import type { Id } from '../api/resource-id'
import { computed, markRaw, onMounted, ref } from 'vue'
import { Coin, DataAnalysis, Goods, User } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import UnifiedState from '../components/common/UnifiedState.vue'
import { dashboardApi, queryApi, semanticApi, syncApi } from '../api/runtime'
import type { Dashboard } from '../dashboard/types'
import type { Metric, MetricVersion } from '../semantic/types'
import type { SyncJob, SyncRun } from '../sync/types'
import { useAppStore } from '../stores/app'

const app = useAppStore()
const router = useRouter()
const loading = ref(true)
const fatalError = ref('')
const partialErrors = ref<string[]>([])
const dashboards = ref<Dashboard[]>([])
const jobs = ref<SyncJob[]>([])
const runs = ref<SyncRun[]>([])
const metricValues = ref<Record<string, { display: string; note: string }>>({})

const metricDefinitions = [
  { key: 'sales', label: '销售额', codes: ['net_sales', 'sales_amount', 'total_sales'], names: ['销售额', '销售金额'], icon: markRaw(Coin), tone: 'blue' },
  { key: 'orders', label: '订单量', codes: ['order_count', 'total_orders'], names: ['订单量', '订单数'], icon: markRaw(Goods), tone: 'teal' },
  { key: 'customers', label: '客户量', codes: ['customer_count', 'unique_customers'], names: ['客户量', '客户数'], icon: markRaw(User), tone: 'violet' },
] as const

const currentTenant = computed(() => app.tenants.find(item => item.id === app.tenantId)?.name ?? '当前企业')
const currentWorkspace = computed(() => app.workspaces.find(item => item.id === app.workspaceId)?.workspaceName ?? '当前工作空间')
const greeting = computed(() => new Date().getHours() < 12 ? '早上好' : new Date().getHours() < 18 ? '下午好' : '晚上好')
const metricCards = computed(() => metricDefinitions.map(item => ({
  ...item,
  display: metricValues.value[item.key]?.display ?? '—',
  note: metricValues.value[item.key]?.note ?? '未配置或未获得指标权限',
})))
const syncRows = computed(() => jobs.value.slice(0, 5).map(job => {
  const run = runs.value.find(item => item.syncJobId === job.id)
  const raw = run?.status ?? job.status
  const success = ['SUCCEEDED', 'PUBLISHED', 'ENABLED'].includes(raw)
  const failed = ['FAILED', 'DEAD_LETTER'].includes(raw)
  return {
    id: job.id, name: job.jobCode, source: job.sourceObject,
    updatedAt: formatDate(run?.finishedAt ?? run?.createdAt ?? job.updatedAt),
    status: run ? runStatusText(raw) : (job.status === 'ENABLED' ? '等待调度' : '已停用'),
    tone: failed ? 'danger' : success ? 'success' : 'warning',
    tagType: failed ? 'danger' : success ? 'success' : 'warning',
  } as const
}))

function findMetric(metrics: Metric[], definition: typeof metricDefinitions[number]): Metric | undefined {
  return metrics.find(metric => definition.codes.includes(metric.code as never))
    ?? metrics.find(metric => definition.names.some(name => metric.name.includes(name)))
}

async function loadMetric(metric: Metric, key: string): Promise<void> {
  const versions = await semanticApi.metricVersions(metric.id)
  const version = versions.find(item => item.id === metric.currentVersionId)
    ?? versions.find(item => item.status === 'PUBLISHED')
  if (!version) return
  const result = await queryApi.execute({
    datasetVersionId: version.datasetVersionId,
    metricVersionIds: [version.id], dimensionFieldIds: [], filters: [], sort: [], limit: 1,
  })
  const column = result.columns.find(item => item.kind === 'METRIC') ?? result.columns[0]
  const value = column ? result.rows[0]?.[column.key] : undefined
  metricValues.value[key] = { display: formatMetric(value, metric.unit), note: result.cached ? '来自查询缓存' : '来自最新授权查询' }
}

async function load(): Promise<void> {
  loading.value = true
  fatalError.value = ''
  partialErrors.value = []
  const [dashboardResult, jobResult, runResult, metricResult] = await Promise.allSettled([
    dashboardApi.listDashboards(app.workspaceId), syncApi.jobs(), syncApi.runs(), semanticApi.metrics(app.workspaceId),
  ])
  if (dashboardResult.status === 'fulfilled') dashboards.value = orderDashboards(dashboardResult.value).slice(0, 5)
  else partialErrors.value.push('最近看板')
  if (jobResult.status === 'fulfilled') jobs.value = [...jobResult.value].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  else partialErrors.value.push('同步任务')
  if (runResult.status === 'fulfilled') runs.value = [...runResult.value].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  else if (jobs.value.length) partialErrors.value.push('任务运行状态')
  if (metricResult.status === 'fulfilled') {
    const requests = metricDefinitions.map(async definition => {
      const metric = findMetric(metricResult.value, definition)
      if (metric) await loadMetric(metric, definition.key)
    })
    const values = await Promise.allSettled(requests)
    if (values.some(item => item.status === 'rejected')) partialErrors.value.push('部分授权指标')
  } else partialErrors.value.push('授权指标')
  if ([dashboardResult, jobResult, metricResult].every(item => item.status === 'rejected')) fatalError.value = '当前工作空间摘要加载失败，请检查服务连接后重试。'
  loading.value = false
}

function orderDashboards(values: Dashboard[]): Dashboard[] {
  const recent = readStoredIds(`aibi.recentDashboards.${app.tenantId}.${app.workspaceId}`)
  const rank = (id: Id) => { const index = recent.indexOf(id); return index < 0 ? Number.MAX_SAFE_INTEGER : index }
  return [...values].sort((a, b) => rank(a.id) - rank(b.id))
}
function openDashboard(id: Id): void {
  const key = `aibi.recentDashboards.${app.tenantId}.${app.workspaceId}`
  const existing = readStoredIds(key)
  localStorage.setItem(key, JSON.stringify([id, ...existing.filter(value => value !== id)].slice(0, 10)))
  void router.push('/dashboards')
}
function statusText(value: string): string { return value === 'PUBLISHED' ? '已发布' : '草稿' }
function runStatusText(value: string): string { return ({ SUCCEEDED: '成功', FAILED: '失败', RUNNING: '运行中', PENDING: '等待中', DEAD_LETTER: '需处理' } as Record<string, string>)[value] ?? value }
function formatDate(value?: string): string { return value ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '暂无运行记录' }
function formatMetric(value: unknown, unit: string): string {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return value === undefined ? '—' : String(value)
  if (unit === 'CNY' || unit === '元') return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 0 }).format(numeric)
  return `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(numeric)}${unit && unit !== 'COUNT' ? ` ${unit}` : ''}`
}

onMounted(load)
</script>
