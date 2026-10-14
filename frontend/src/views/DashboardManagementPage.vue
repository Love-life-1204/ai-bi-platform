<template>
  <div class="dashboard-management-page">
    <template v-if="screen === 'list'">
      <section class="dashboard-hero">
        <div>
          <span class="eyebrow">TRUSTED BI WORKSPACE</span>
          <h2>可视化数据看板</h2>
          <p>将已发布图表编排为权限感知、可筛选、可联动的企业数据视图。</p>
        </div>
        <div class="dashboard-stats">
          <span><strong>{{ dashboards.length }}</strong><small>全部看板</small></span>
          <span><strong>{{ publishedCount }}</strong><small>已发布</small></span>
          <span><strong>{{ publishedCharts.length }}</strong><small>可用图表</small></span>
        </div>
      </section>

      <section class="workbench-card dashboard-list-card">
        <header class="dashboard-list-heading">
          <div><span class="eyebrow">DASHBOARD LIBRARY</span><h3>{{ workspaceName }}</h3><p>正式查看始终重新执行当前成员权限下的统一查询。</p></div>
          <el-button v-if="!mobileReadonly" type="primary" :icon="Plus" @click="openCreate">新建看板</el-button>
        </header>
        <div class="dashboard-list-toolbar">
          <el-input v-model="filters.keyword" clearable placeholder="搜索看板名称或编码" />
          <el-segmented v-model="filters.category" :options="categoryOptions" />
        </div>
        <UnifiedState v-if="loading" state="loading" description="正在加载看板与已发布图表。" />
        <UnifiedState v-else-if="loadError && !dashboards.length" state="error" :description="loadError" retryable @retry="load" />
        <template v-else>
          <el-alert v-if="loadError" type="warning" :title="loadError" show-icon :closable="false" />
          <UnifiedState v-if="!filteredDashboards.length" state="empty" title="暂无看板" description="从已发布图表创建第一个业务看板。" :action-text="mobileReadonly ? '' : '新建看板'" @action="openCreate" />
          <div v-else class="dashboard-library-grid">
            <article v-for="item in filteredDashboards" :key="item.id" class="dashboard-library-card">
              <header><span class="dashboard-card-icon"><el-icon><DataBoard /></el-icon></span><el-tag :type="item.status === 'PUBLISHED' ? 'success' : 'info'">{{ item.status === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></header>
              <div><h3>{{ item.name }}</h3><code>{{ item.code }}</code></div>
              <dl><div><dt>当前版本</dt><dd>{{ item.currentVersionId ? `#${item.currentVersionId}` : '未发布' }}</dd></div><div><dt>最近更新</dt><dd>{{ formatDate(item.updatedAt) }}</dd></div></dl>
              <footer><el-button v-if="item.currentVersionId" @click="openDashboard(item, false)">查看</el-button><el-button v-if="!mobileReadonly" type="primary" @click="openDashboard(item, true)">编辑</el-button><el-button link @click="showVersions(item)">版本</el-button></footer>
            </article>
          </div>
        </template>
      </section>
    </template>

    <template v-else>
      <section class="dashboard-workspace-heading">
        <div class="dashboard-title-row">
          <el-button :icon="ArrowLeft" circle aria-label="返回看板列表" @click="closeWorkspace" />
          <div><span class="eyebrow">{{ canEdit ? 'DASHBOARD EDITOR' : 'PUBLISHED VIEW' }}</span><h2>{{ activeData?.dashboard.name }}</h2><p>{{ viewMode === 'draft' ? '草稿预览' : '正式发布版' }} · V{{ visibleVersion?.versionNo ?? '—' }}</p></div>
        </div>
        <div class="dashboard-workspace-actions">
          <el-tag v-if="dirty" type="warning">有未保存更改</el-tag>
          <el-select v-model="refreshSeconds" class="dashboard-refresh-select" @change="configureAutoRefresh"><el-option label="手动刷新" :value="0" /><el-option label="每 60 秒" :value="60" /><el-option label="每 2 分钟" :value="120" /><el-option label="每 5 分钟" :value="300" /></el-select>
          <el-button :icon="Refresh" :loading="querying" @click="refreshDashboard">刷新</el-button>
          <el-button v-if="activeData?.current && activeData?.draft" @click="toggleVersionMode">{{ viewMode === 'draft' ? '查看正式版' : '返回草稿' }}</el-button>
          <el-button v-if="canEdit" :loading="saving" :disabled="!dirty" @click="saveDraft">保存布局</el-button>
          <el-button v-if="canEdit" type="success" @click="publishActive">发布</el-button>
          <el-button @click="showVersions(activeData!.dashboard)">历史版本</el-button>
        </div>
      </section>

      <el-alert v-if="mobileReadonly" type="info" title="移动端为只读看板：可刷新、筛选、联动和下钻，布局编辑请使用桌面端。" :closable="false" show-icon />

      <section class="dashboard-runtime-filters">
        <div v-for="definition in visibleVersion?.filters ?? []" :key="definition.key" class="dashboard-filter-control">
          <label>{{ definition.label }}</label>
          <el-date-picker v-if="definition.type === 'DATE_RANGE'" v-model="timeRange" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" range-separator="至" />
          <el-select v-else-if="definition.type === 'MULTI_SELECT'" v-model="multiValues[definition.key]" multiple filterable allow-create default-first-option collapse-tags placeholder="输入并选择多个值" />
          <el-select v-else v-model="singleValues[definition.key]" filterable allow-create default-first-option clearable placeholder="输入筛选值" />
        </div>
        <div class="dashboard-filter-actions"><el-button type="primary" @click="applyFilters">应用筛选</el-button><el-button @click="resetFilters">重置</el-button><el-button v-if="canEdit" text type="primary" @click="openFilterEditor">配置筛选器</el-button></div>
        <div v-if="interactionFilters.length" class="dashboard-interaction-tags"><span>图表联动</span><el-tag v-for="item in interactionFilters" :key="item.fieldId" closable @close="removeInteraction(item.fieldId)">字段 {{ item.fieldId }} = {{ String(item.value) }}</el-tag></div>
      </section>

      <section v-if="canEdit" class="dashboard-editor-tools">
        <div><strong>12 列画布</strong><span>拖拽组件移动；工具栏可调整宽高与位置，变更会自动保存。</span></div>
        <el-button type="primary" plain :icon="Plus" @click="openAddWidget">添加图表</el-button>
      </section>

      <UnifiedState v-if="!visibleVersion" state="empty" title="没有可查看的版本" description="请返回列表创建或发布看板。" />
      <section
        v-else
        class="dashboard-canvas"
        :class="{ editable: canEdit }"
        :style="{ '--dashboard-row-height': `${visibleVersion.layout.rowHeight ?? 72}px` }"
        @dragover.prevent
        @drop="dropWidget"
      >
        <article
          v-for="widget in visibleVersion.widgets"
          :key="widget.key"
          class="dashboard-widget-card"
          :class="{ dragging: draggingKey === widget.key }"
          :style="widgetStyle(widget)"
          :draggable="canEdit"
          @dragstart="startDrag(widget.key)"
          @dragend="draggingKey = ''"
        >
          <header>
            <div><span class="widget-grip" aria-hidden="true">⠿</span><strong>{{ chartName(widget.chartVersionId) }}</strong><small>{{ widget.key }}</small></div>
            <div v-if="canEdit" class="widget-edit-actions">
              <el-button-group><el-button size="small" aria-label="左移" @click="moveWidget(widget, -1, 0)">←</el-button><el-button size="small" aria-label="右移" @click="moveWidget(widget, 1, 0)">→</el-button><el-button size="small" aria-label="上移" @click="moveWidget(widget, 0, -1)">↑</el-button><el-button size="small" aria-label="下移" @click="moveWidget(widget, 0, 1)">↓</el-button></el-button-group>
              <el-dropdown trigger="click"><el-button size="small">尺寸</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="resizeWidget(widget, -1, 0)">缩窄</el-dropdown-item><el-dropdown-item @click="resizeWidget(widget, 1, 0)">加宽</el-dropdown-item><el-dropdown-item @click="resizeWidget(widget, 0, -1)">降低</el-dropdown-item><el-dropdown-item @click="resizeWidget(widget, 0, 1)">增高</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
              <el-button size="small" type="danger" plain @click="removeWidget(widget)">删除</el-button>
            </div>
          </header>
          <div class="dashboard-widget-body">
            <ChartPreview
              :spec="widgetState(widget.key).spec ?? fallbackSpec"
              :result="widgetState(widget.key).result"
              :loading="widgetState(widget.key).loading"
              :error="widgetState(widget.key).error"
              @retry="refreshDashboard"
              @filter="(_field, value) => applyInteraction(widget.key, value)"
              @drill="showDrill"
            />
          </div>
          <footer><span>{{ widget.position.w }} × {{ widget.position.h }}</span><span v-if="widgetState(widget.key).result">{{ widgetState(widget.key).result!.rowCount }} 行 · {{ widgetState(widget.key).result!.executionMs }} ms</span><span v-else-if="widgetState(widget.key).error">局部加载失败</span></footer>
        </article>
        <UnifiedState v-if="!visibleVersion.widgets.length" state="empty" title="画布为空" description="添加已发布图表开始编排看板。" />
      </section>
    </template>

    <CommonDialog v-model="createOpen" title="新建看板" width="760px">
      <el-form label-position="top">
        <div class="dashboard-create-grid"><el-form-item label="看板名称"><el-input v-model.trim="createForm.name" maxlength="200" placeholder="例如：经营总览" /></el-form-item><el-form-item label="看板编码"><el-input v-model.trim="createForm.code" maxlength="64" placeholder="business_overview" /></el-form-item></div>
        <el-form-item label="初始图表组件"><el-select v-model="createForm.chartVersionIds" multiple filterable collapse-tags placeholder="选择已发布图表"><el-option v-for="item in publishedCharts" :key="item.versionId" :label="item.chart.name" :value="item.versionId" /></el-select></el-form-item>
        <el-alert v-if="!publishedCharts.length" type="warning" title="请先在图表设计器发布至少一个图表。" show-icon :closable="false" />
      </el-form>
      <template #footer><el-button @click="createOpen = false">取消</el-button><el-button type="primary" :loading="creating" :disabled="!canCreate" @click="createDashboard">创建并编辑</el-button></template>
    </CommonDialog>

    <CommonDialog v-model="addWidgetOpen" title="添加已发布图表" width="620px">
      <el-select v-model="selectedChartVersionId" filterable placeholder="选择图表" style="width: 100%"><el-option v-for="item in addableCharts" :key="item.versionId" :label="item.chart.name" :value="item.versionId" /></el-select>
      <template #footer><el-button @click="addWidgetOpen = false">取消</el-button><el-button type="primary" :disabled="!selectedChartVersionId" @click="addWidget">添加到画布</el-button></template>
    </CommonDialog>

    <CommonDialog v-model="filterEditorOpen" title="全局筛选器" width="860px">
      <div class="dashboard-filter-definitions">
        <div v-for="(item, index) in filterDrafts" :key="`${item.key}-${index}`" class="dashboard-filter-definition-row"><el-input v-model.trim="item.label" placeholder="显示名称" /><el-input v-model.trim="item.key" placeholder="唯一键" /><el-input v-model="item.fieldId" inputmode="numeric" /><el-select v-model="item.type"><el-option label="时间范围" value="DATE_RANGE" /><el-option label="单选维度" value="SELECT" /><el-option label="多选维度" value="MULTI_SELECT" /></el-select><el-button type="danger" text @click="filterDrafts.splice(index, 1)">删除</el-button></div>
        <el-button plain :icon="Plus" @click="addFilterDefinition">添加筛选器</el-button>
      </div>
      <template #footer><el-button @click="filterEditorOpen = false">取消</el-button><el-button type="primary" @click="applyFilterDefinitions">保存筛选器</el-button></template>
    </CommonDialog>

    <CommonDialog v-model="versionsOpen" :title="`历史版本 · ${versionsDashboard?.name ?? ''}`" width="900px">
      <el-table :data="selectedVersions" stripe><el-table-column label="版本" width="90"><template #default="{ row }">V{{ row.versionNo }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></template></el-table-column><el-table-column label="组件" width="100"><template #default="{ row }">{{ row.widgets.length }}</template></el-table-column><el-table-column label="筛选器" width="100"><template #default="{ row }">{{ row.filters.length }}</template></el-table-column><el-table-column label="网格" width="120"><template #default="{ row }">{{ row.layout.columns }} 列</template></el-table-column><el-table-column label="发布时间" min-width="180"><template #default="{ row }">{{ row.publishedAt ? formatDate(row.publishedAt) : '未发布' }}</template></el-table-column></el-table>
      <template #footer><el-button type="primary" @click="versionsOpen = false">关闭</el-button></template>
    </CommonDialog>

    <CommonDialog v-model="drillOpen" :title="drillTitle" width="760px">
      <el-alert type="info" title="明细仅来自当前成员已授权的本次查询结果，不会绕过行列权限重新取数。" show-icon :closable="false" />
      <el-table :data="drillRows" stripe max-height="420"><el-table-column prop="field" label="字段" min-width="220" /><el-table-column prop="value" label="值" min-width="300" /></el-table>
      <template #footer><el-button type="primary" @click="drillOpen = false">关闭</el-button></template>
    </CommonDialog>
  </div>
</template>

<script setup lang="ts">
import { isResourceId } from '../api/resource-id'
import type { Id } from '../api/resource-id'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ArrowLeft, DataBoard, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChartPreview from '../components/common/ChartPreview.vue'
import CommonDialog from '../components/common/CommonDialog.vue'
import UnifiedState from '../components/common/UnifiedState.vue'
import { dashboardApi } from '../api/runtime'
import { ApiClientError } from '../api/client'
import { movePosition, nextPosition, resizePosition, resolveWidgetResult } from '../dashboard/dashboard-layout'
import type {
  Chart, ChartSpec, Dashboard, DashboardData, DashboardQueryInput, DashboardVersion,
  DashboardWidget, GlobalFilterDefinition, WidgetResult,
} from '../dashboard/types'
import type { QueryFilter, QueryResult } from '../query/types'
import { useConfirm } from '../composables/useConfirm'
import { useAppStore } from '../stores/app'

interface PublishedChart { chart: Chart; versionId: Id }
interface WidgetState { loading: boolean; error: string; retryable: boolean; result?: QueryResult; spec?: ChartSpec }

const app = useAppStore()
const { confirm } = useConfirm()
const dashboards = ref<Dashboard[]>([])
const charts = ref<Chart[]>([])
const loading = ref(true)
const loadError = ref('')
const filters = reactive({ keyword: '', category: 'ALL' as 'ALL' | 'DRAFT' | 'PUBLISHED' | 'MINE' })
const categoryOptions = [{ label: '全部', value: 'ALL' }, { label: '草稿', value: 'DRAFT' }, { label: '已发布', value: 'PUBLISHED' }, { label: '我创建的', value: 'MINE' }]
const screen = ref<'list' | 'workspace'>('list')
const activeData = ref<DashboardData>()
const workingVersion = ref<DashboardVersion>()
const viewMode = ref<'draft' | 'published'>('published')
const dirty = ref(false)
const saving = ref(false)
let saveQueued = false
let saveTimer: ReturnType<typeof setTimeout> | undefined

const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', code: '', chartVersionIds: [] as Id[] })
const addWidgetOpen = ref(false)
const selectedChartVersionId = ref<Id>()
const filterEditorOpen = ref(false)
const filterDrafts = ref<GlobalFilterDefinition[]>([])
const versionsOpen = ref(false)
const versionsDashboard = ref<Dashboard>()
const selectedVersions = ref<DashboardVersion[]>([])

const widgetStates = reactive<Record<string, WidgetState>>({})
const querying = ref(false)
let queryController: AbortController | undefined
let querySequence = 0
const timeRange = ref<[Date, Date]>()
const singleValues = reactive<Record<string, string>>({})
const multiValues = reactive<Record<string, string[]>>({})
const interactionFilters = ref<QueryFilter[]>([])
const refreshSeconds = ref(0)
let refreshTimer: ReturnType<typeof setInterval> | undefined
const draggingKey = ref('')

const drillOpen = ref(false)
const drillTitle = ref('权限明细')
const drillRows = ref<{ field: string; value: string }[]>([])
const fallbackSpec: ChartSpec = { type: 'KPI', valueFields: [], emptyText: '等待查询结果' }

const workspaceName = computed(() => app.workspaces.find(item => item.id === app.workspaceId)?.workspaceName ?? '当前工作空间')
const publishedCount = computed(() => dashboards.value.filter(item => item.status === 'PUBLISHED').length)
const publishedCharts = computed<PublishedChart[]>(() => charts.value.flatMap(chart => chart.currentVersionId ? [{ chart, versionId: chart.currentVersionId }] : []))
const filteredDashboards = computed(() => dashboards.value.filter(item => {
  const keywordMatch = !filters.keyword || `${item.name} ${item.code}`.toLowerCase().includes(filters.keyword.toLowerCase())
  const categoryMatch = filters.category === 'ALL' || (filters.category === 'MINE' ? item.ownerMemberId === app.currentMember?.id : item.status === filters.category)
  return keywordMatch && categoryMatch
}))
const visibleVersion = computed(() => viewMode.value === 'draft' ? workingVersion.value : activeData.value?.current)
const editable = computed(() => screen.value === 'workspace' && viewMode.value === 'draft' && Boolean(workingVersion.value))
const mobileReadonly = ref(globalThis.matchMedia?.('(max-width: 899px)').matches ?? false)
const mobileQuery = globalThis.matchMedia?.('(max-width: 899px)')
const updateMobileReadonly = (event: MediaQueryListEvent): void => { mobileReadonly.value = event.matches }
mobileQuery?.addEventListener('change', updateMobileReadonly)
const canEdit = computed(() => editable.value && !mobileReadonly.value)
const canCreate = computed(() => Boolean(createForm.name.trim() && /^[a-z][a-z0-9_]{1,63}$/.test(createForm.code)))
const addableCharts = computed(() => publishedCharts.value.filter(item => !workingVersion.value?.widgets.some(widget => widget.chartVersionId === item.versionId)))

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  const [dashboardResult, chartResult] = await Promise.allSettled([
    dashboardApi.listDashboards(app.workspaceId),
    dashboardApi.listCharts(app.workspaceId),
  ])
  if (dashboardResult.status === 'fulfilled') dashboards.value = dashboardResult.value
  if (chartResult.status === 'fulfilled') charts.value = chartResult.value
  const failure = [dashboardResult, chartResult].find(item => item.status === 'rejected') as PromiseRejectedResult | undefined
  if (failure) loadError.value = failure.reason instanceof Error ? failure.reason.message : '看板信息加载失败'
  loading.value = false
}

function openCreate(): void {
  Object.assign(createForm, { name: '', code: '', chartVersionIds: [] })
  createOpen.value = true
}

async function createDashboard(): Promise<void> {
  if (!canCreate.value) return
  creating.value = true
  try {
    const widgets = createForm.chartVersionIds.map((chartVersionId, index) => ({
      key: `widget_${index + 1}`,
      chartVersionId,
      position: { x: (index % 2) * 6, y: Math.floor(index / 2) * 4, w: 6, h: 4 },
    }))
    const data = await dashboardApi.createDashboard({ workspaceId: app.workspaceId, code: createForm.code, name: createForm.name, layout: { columns: 12, rowHeight: 72 }, filters: [], widgets })
    createOpen.value = false
    enterWorkspace(data, true)
    ElMessage.success('看板草稿已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '看板创建失败') }
  finally { creating.value = false }
}

async function openDashboard(dashboard: Dashboard, edit: boolean): Promise<void> {
  try {
    let data = await dashboardApi.getDashboard(dashboard.id)
    if (edit && !data.draft && data.current) {
      data = await dashboardApi.updateDashboard(dashboard.id, {
        name: dashboard.name,
        layout: { columns: 12, rowHeight: data.current.layout.rowHeight ?? 72 },
        filters: data.current.filters,
        widgets: data.current.widgets.map(widget => ({ key: widget.key, chartVersionId: widget.chartVersionId, position: widget.position })),
        lockVersion: data.dashboard.lockVersion,
      })
    }
    enterWorkspace(data, edit)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '看板打开失败') }
}

function enterWorkspace(data: DashboardData, edit: boolean): void {
  activeData.value = data
  workingVersion.value = data.draft ? cloneVersion(data.draft) : undefined
  viewMode.value = edit && data.draft ? 'draft' : data.current ? 'published' : 'draft'
  screen.value = 'workspace'
  dirty.value = false
  resetFilters(false)
  syncWidgetStates()
  void refreshDashboard()
}

async function closeWorkspace(force = false): Promise<void> {
  if (!force && dirty.value) {
    if (!await confirm('当前看板有未保存更改，保存后返回列表？', { title: '返回看板列表', confirmText: '保存并返回' })) return
    await saveDraft()
    if (dirty.value) return
  }
  if (saveTimer) clearTimeout(saveTimer)
  queryController?.abort()
  clearAutoRefresh()
  screen.value = 'list'
  activeData.value = undefined
  workingVersion.value = undefined
  void load()
}

function toggleVersionMode(): void {
  viewMode.value = viewMode.value === 'draft' ? 'published' : 'draft'
  resetFilters(false)
  syncWidgetStates()
  void refreshDashboard()
}

function widgetStyle(widget: DashboardWidget): Record<string, string> {
  return { gridColumn: `${widget.position.x + 1} / span ${widget.position.w}`, gridRow: `${widget.position.y + 1} / span ${widget.position.h}` }
}

function startDrag(key: string): void { if (canEdit.value) draggingKey.value = key }
function dropWidget(event: DragEvent): void {
  if (!canEdit.value || !draggingKey.value || !workingVersion.value) return
  const grid = event.currentTarget as HTMLElement
  const bounds = grid.getBoundingClientRect()
  const x = Math.floor((event.clientX - bounds.left) / bounds.width * 12)
  const rowHeight = workingVersion.value.layout.rowHeight ?? 72
  const y = Math.floor((event.clientY - bounds.top) / rowHeight)
  const widget = workingVersion.value.widgets.find(item => item.key === draggingKey.value)
  if (widget) widget.position = movePosition(widget.position, x, y)
  draggingKey.value = ''
  markDirty()
}

function moveWidget(widget: DashboardWidget, dx: number, dy: number): void { widget.position = movePosition(widget.position, widget.position.x + dx, widget.position.y + dy); markDirty() }
function resizeWidget(widget: DashboardWidget, dw: number, dh: number): void { widget.position = resizePosition(widget.position, dw, dh); markDirty() }

function openAddWidget(): void { selectedChartVersionId.value = addableCharts.value[0]?.versionId; addWidgetOpen.value = true }
function addWidget(): void {
  if (!workingVersion.value || !selectedChartVersionId.value) return
  workingVersion.value.widgets.push({ id: `local-widget-${Date.now()}`, chartVersionId: selectedChartVersionId.value, key: `widget_${Date.now().toString(36)}`, position: nextPosition(workingVersion.value.widgets) })
  addWidgetOpen.value = false
  syncWidgetStates()
  markDirty()
  void refreshDashboard()
}

async function removeWidget(widget: DashboardWidget): Promise<void> {
  if (!workingVersion.value || !await confirm(`从看板移除“${chartName(widget.chartVersionId)}”？图表本身不会被删除。`, { title: '移除组件', danger: true })) return
  workingVersion.value.widgets = workingVersion.value.widgets.filter(item => item.key !== widget.key)
  delete widgetStates[widget.key]
  markDirty()
}

function markDirty(): void {
  dirty.value = true
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => void saveDraft(), 800)
}

async function saveDraft(): Promise<void> {
  if (!activeData.value || !workingVersion.value || !dirty.value) return
  if (saving.value) { saveQueued = true; return }
  saving.value = true
  const snapshot = cloneVersion(workingVersion.value)
  try {
    const updated = await dashboardApi.updateDashboard(activeData.value.dashboard.id, {
      name: activeData.value.dashboard.name,
      layout: { columns: 12, rowHeight: snapshot.layout.rowHeight ?? 72 },
      filters: snapshot.filters,
      widgets: snapshot.widgets.map(widget => ({ key: widget.key, chartVersionId: widget.chartVersionId, position: widget.position })),
      lockVersion: activeData.value.dashboard.lockVersion,
    })
    activeData.value = { ...updated, draft: workingVersion.value }
    if (!saveQueued && updated.draft) {
      workingVersion.value = cloneVersion(updated.draft)
      dirty.value = false
      syncWidgetStates()
      void refreshDashboard()
    }
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '看板草稿保存失败') }
  finally {
    saving.value = false
    if (saveQueued) { saveQueued = false; void saveDraft() }
  }
}

async function publishActive(): Promise<void> {
  if (!activeData.value) return
  if (dirty.value) await saveDraft()
  if (dirty.value || saving.value) { ElMessage.warning('请等待草稿保存完成后再发布'); return }
  if (!await confirm(`发布“${activeData.value.dashboard.name}”后，当前布局、筛选器和图表版本将成为不可变版本。`, { title: '发布看板', confirmText: '确认发布' })) return
  try {
    await dashboardApi.publishDashboard(activeData.value.dashboard.id)
    const data = await dashboardApi.getDashboard(activeData.value.dashboard.id)
    activeData.value = data; workingVersion.value = undefined; viewMode.value = 'published'; dirty.value = false
    syncWidgetStates(); await refreshDashboard(); ElMessage.success('看板已发布')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '看板发布失败') }
}

function openFilterEditor(): void { filterDrafts.value = (workingVersion.value?.filters ?? []).map(item => ({ ...item })); filterEditorOpen.value = true }
function addFilterDefinition(): void { filterDrafts.value.push({ key: `filter_${filterDrafts.value.length + 1}`, label: '新筛选器', fieldId: '', type: 'SELECT' }) }
function applyFilterDefinitions(): void {
  const keys = filterDrafts.value.map(item => item.key)
  if (keys.some((key, index) => !/^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(key) || keys.indexOf(key) !== index) || filterDrafts.value.some(item => !item.label.trim() || !isResourceId(item.fieldId))) {
    ElMessage.warning('筛选器名称、唯一键或字段 ID 不合法'); return
  }
  if (!workingVersion.value) return
  workingVersion.value.filters = filterDrafts.value.map(item => ({ ...item }))
  filterEditorOpen.value = false; resetFilters(false); markDirty()
}

function dashboardQueryInput(): DashboardQueryInput {
  const definitions = visibleVersion.value?.filters ?? []
  const dimensionFilters: QueryFilter[] = definitions.flatMap<QueryFilter>(definition => {
    if (definition.type === 'DATE_RANGE') return []
    if (definition.type === 'MULTI_SELECT') {
      const values = multiValues[definition.key] ?? []
      return values.length ? [{ fieldId: definition.fieldId, operator: 'IN', value: values }] : []
    }
    const value = singleValues[definition.key]?.trim()
    return value ? [{ fieldId: definition.fieldId, operator: 'EQ', value }] : []
  })
  const timeDefinition = definitions.find(item => item.type === 'DATE_RANGE')
  return {
    ...(timeDefinition && timeRange.value ? { timeRange: { fieldId: timeDefinition.fieldId, start: timeRange.value[0].toISOString(), end: timeRange.value[1].toISOString() } } : {}),
    filters: dimensionFilters,
    interactionFilters: interactionFilters.value,
    previewDraft: viewMode.value === 'draft',
  }
}

async function refreshDashboard(): Promise<void> {
  if (!activeData.value || !visibleVersion.value) return
  const sequence = ++querySequence
  queryController?.abort(); queryController = new AbortController(); querying.value = true
  visibleVersion.value.widgets.forEach(widget => Object.assign(widgetState(widget.key), { loading: true, error: '' }))
  try {
    const response = await dashboardApi.queryDashboard(activeData.value.dashboard.id, dashboardQueryInput(), queryController.signal)
    if (sequence !== querySequence) return
    const byKey = new Map(response.widgets.map(item => [item.key, item]))
    visibleVersion.value.widgets.forEach(widget => applyWidgetResult(widget.key, byKey.get(widget.key)))
  } catch (error) {
    if (sequence !== querySequence || (error instanceof ApiClientError && error.code === 'REQUEST_ABORTED')) return
    const message = error instanceof ApiClientError && error.code === 'REQUEST_TIMEOUT' ? '看板查询超时，请缩小时间范围后重试' : error instanceof Error ? error.message : '看板加载失败'
    visibleVersion.value.widgets.forEach(widget => Object.assign(widgetState(widget.key), { loading: false, error: message, retryable: true, result: undefined }))
  } finally { if (sequence === querySequence) querying.value = false }
}

function applyWidgetResult(key: string, value?: WidgetResult): void {
  const state = widgetState(key)
  delete state.result
  delete state.spec
  Object.assign(state, resolveWidgetResult(value))
}

function applyFilters(): void { interactionFilters.value = []; void refreshDashboard() }
function resetFilters(refresh = true): void {
  timeRange.value = undefined
  Object.keys(singleValues).forEach(key => delete singleValues[key])
  Object.keys(multiValues).forEach(key => delete multiValues[key])
  interactionFilters.value = []
  if (refresh) void refreshDashboard()
}
function applyInteraction(widgetKey: string, value: unknown): void {
  const fieldId = widgetState(widgetKey).spec?.interactionFieldId
  if (!fieldId) return
  interactionFilters.value = [...interactionFilters.value.filter(item => item.fieldId !== fieldId), { fieldId, operator: 'EQ', value }]
  void refreshDashboard()
}
function removeInteraction(fieldId: Id): void { interactionFilters.value = interactionFilters.value.filter(item => item.fieldId !== fieldId); void refreshDashboard() }

function showDrill(field: string, value: unknown, row: Record<string, unknown>): void {
  drillTitle.value = `权限明细 · ${field} = ${String(value ?? '')}`
  drillRows.value = Object.entries(row).map(([key, cell]) => ({ field: key, value: String(cell ?? '') }))
  drillOpen.value = true
}

function configureAutoRefresh(): void {
  clearAutoRefresh()
  if (refreshSeconds.value > 0) refreshTimer = setInterval(() => { if (!document.hidden) void refreshDashboard() }, refreshSeconds.value * 1000)
}
function clearAutoRefresh(): void { if (refreshTimer) clearInterval(refreshTimer); refreshTimer = undefined }
function syncWidgetStates(): void {
  const keys = new Set(visibleVersion.value?.widgets.map(item => item.key) ?? [])
  Object.keys(widgetStates).filter(key => !keys.has(key)).forEach(key => delete widgetStates[key])
  keys.forEach(key => { widgetStates[key] ??= { loading: true, error: '', retryable: false } })
}
function widgetState(key: string): WidgetState { return widgetStates[key] ??= { loading: true, error: '', retryable: false } }
function chartName(versionId: Id): string { return publishedCharts.value.find(item => item.versionId === versionId)?.chart.name ?? `图表版本 #${versionId}` }

async function showVersions(dashboard: Dashboard): Promise<void> {
  versionsDashboard.value = dashboard
  try { selectedVersions.value = await dashboardApi.listDashboardVersions(dashboard.id); versionsOpen.value = true }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '历史版本加载失败') }
}

function cloneVersion(value: DashboardVersion): DashboardVersion { return structuredClone(value) }
function formatDate(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) }

watch(() => app.contextVersion, () => { if (screen.value === 'workspace') void closeWorkspace(true); else void load() })
onBeforeUnmount(() => { queryController?.abort(); clearAutoRefresh(); if (saveTimer) clearTimeout(saveTimer); mobileQuery?.removeEventListener('change', updateMobileReadonly) })
void load()
</script>
