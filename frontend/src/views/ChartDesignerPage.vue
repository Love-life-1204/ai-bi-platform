<template>
  <div class="chart-designer-page">
    <section class="chart-designer-hero">
      <div><span class="eyebrow">VISUAL ANALYTICS</span><h2>图表设计器</h2><p>从已发布数据集和指标构建安全查询，实时预览并发布不可变图表版本。</p></div>
      <div class="chart-designer-stats"><span><strong>{{ charts.length }}</strong><small>图表</small></span><span><strong>{{ publishedCount }}</strong><small>已发布</small></span><span><strong>7</strong><small>图表类型</small></span></div>
    </section>

    <section class="workbench-card chart-list-card">
      <header class="chart-list-heading"><div><span class="eyebrow">CHART LIBRARY</span><h3>{{ workspaceName }}</h3><p>每个发布版本固定数据集、指标版本、查询计划和展示配置。</p></div><el-button type="primary" :icon="Plus" @click="openCreate">新建图表</el-button></header>
      <div class="chart-list-filters"><el-input v-model="filters.keyword" clearable placeholder="搜索图表名称或编码" /><el-select v-model="filters.type" clearable placeholder="全部类型"><el-option v-for="item in chartTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-select v-model="filters.status" clearable placeholder="全部状态"><el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /></el-select></div>
      <UnifiedState v-if="loading" state="loading" description="正在加载图表、数据集和已发布指标版本。" />
      <UnifiedState v-else-if="loadError && !charts.length" state="error" :description="loadError" retryable @retry="load" />
      <template v-else>
        <el-alert v-if="loadError" type="warning" :title="loadError" show-icon :closable="false" />
        <UnifiedState v-if="!filteredRows.length" state="empty" title="暂无图表" description="选择已发布指标，创建可复用图表版本。" action-text="新建图表" @action="openCreate" />
        <el-table v-else :data="filteredRows" stripe>
          <el-table-column label="图表" min-width="180"><template #default="{ row }"><strong>{{ row.name }}</strong><small class="table-subtext">{{ row.code }}</small></template></el-table-column>
          <el-table-column label="类型" width="120"><template #default="{ row }"><el-tag effect="plain">{{ typeLabel(row.type) }}</el-tag></template></el-table-column>
          <el-table-column prop="sourceName" label="来源数据集" min-width="190" />
          <el-table-column label="版本" width="100"><template #default="{ row }">{{ row.versionText }}</template></el-table-column>
          <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></template></el-table-column>
          <el-table-column label="更新时间" width="170"><template #default="{ row }">{{ formatDate(row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="280" align="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link @click="validateChart(row)">校验</el-button><el-button link @click="showVersions(row)">版本</el-button><el-button link type="success" @click="publishChart(row)">发布</el-button></template></el-table-column>
        </el-table>
      </template>
    </section>

    <CommonDialog v-model="designerOpen" :title="editingChart ? `编辑图表 · ${editingChart.name}` : '新建图表'" width="1260px">
      <div class="chart-designer-layout">
        <aside class="chart-config-panel">
          <section class="chart-config-section"><header><span>01</span><div><strong>基础信息</strong><small>命名与来源</small></div></header><el-form-item label="图表名称"><el-input v-model.trim="form.name" maxlength="200" placeholder="例如：区域销售趋势" /></el-form-item><el-form-item label="图表编码"><el-input v-model.trim="form.code" :disabled="Boolean(editingChart)" maxlength="64" placeholder="regional_sales_trend" /></el-form-item><el-form-item label="已发布数据集版本"><el-select v-model="form.datasetVersionId" filterable placeholder="选择数据集 / 版本" @change="applyDataset"><el-option v-for="item in datasetOptions" :key="item.version.id" :label="`${item.dataset.name} · V${item.version.versionNo}`" :value="item.version.id" /></el-select></el-form-item></section>

          <section class="chart-config-section"><header><span>02</span><div><strong>图表类型</strong><small>七种安全组件</small></div></header><div class="chart-type-grid"><button v-for="item in chartTypes" :key="item.value" type="button" :class="{ active: form.type === item.value }" @click="selectType(item.value)"><el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span></button></div></section>

          <section class="chart-config-section"><header><span>03</span><div><strong>字段绑定</strong><small>维度与已发布指标</small></div></header><el-form-item label="Y 轴 / 数值指标"><el-select v-model="form.metricVersionIds" multiple filterable collapse-tags :multiple-limit="singleMetricType ? 1 : 6" placeholder="选择指标"><el-option v-for="item in availableMetricOptions" :key="item.version.id" :label="`${item.metric.name} · V${item.version.versionNo}`" :value="item.version.id" /></el-select></el-form-item><el-form-item v-if="needsCategory" :label="form.type === 'PIVOT_TABLE' ? '行维度' : 'X 轴 / 分类维度'"><el-select v-model="form.categoryFieldId" filterable placeholder="选择维度"><el-option v-for="field in dimensionFields" :key="field.id" :label="field.businessName" :value="field.id" /></el-select></el-form-item><el-form-item v-if="form.type === 'PIVOT_TABLE'" label="列维度"><el-select v-model="form.seriesFieldId" filterable placeholder="选择另一维度"><el-option v-for="field in dimensionFields" :key="field.id" :label="field.businessName" :value="field.id" /></el-select></el-form-item><el-form-item v-if="form.type === 'TABLE'" label="明细维度列"><el-select v-model="form.detailFieldIds" multiple filterable collapse-tags><el-option v-for="field in dimensionFields" :key="field.id" :label="field.businessName" :value="field.id" /></el-select></el-form-item></section>

          <section class="chart-config-section"><header><span>04</span><div><strong>排序与显示</strong><small>安全声明式配置</small></div></header><el-form-item label="图内标题"><el-input v-model.trim="form.title" maxlength="200" placeholder="留空时使用图表名称" /></el-form-item><div class="chart-inline-grid"><el-form-item label="排序字段"><el-select v-model="form.sortTarget" clearable placeholder="默认顺序"><el-option-group label="维度"><el-option v-for="field in selectedDimensionFields" :key="`FIELD:${field.id}`" :label="field.businessName" :value="`FIELD:${field.id}`" /></el-option-group><el-option-group label="指标"><el-option v-for="item in selectedMetricOptions" :key="`METRIC:${item.version.id}`" :label="item.metric.name" :value="`METRIC:${item.version.id}`" /></el-option-group></el-select></el-form-item><el-form-item label="方向"><el-select v-model="form.sortDirection"><el-option label="升序" value="ASC" /><el-option label="降序" value="DESC" /></el-select></el-form-item></div><el-form-item label="结果行数"><el-slider v-model="form.limit" :min="1" :max="5000" :step="50" show-input /></el-form-item><div class="chart-inline-grid"><el-form-item label="X 轴标题"><el-input v-model.trim="form.xAxisTitle" /></el-form-item><el-form-item label="Y 轴标题"><el-input v-model.trim="form.yAxisTitle" /></el-form-item><el-form-item label="单位"><el-input v-model.trim="form.unit" placeholder="CNY、%、单" /></el-form-item><el-form-item label="显示格式"><el-select v-model="form.valueFormat"><el-option label="数值" value="NUMBER" /><el-option label="货币" value="CURRENCY" /><el-option label="百分比" value="PERCENT" /></el-select></el-form-item><el-form-item label="小数位"><el-input-number v-model="form.decimalPlaces" :min="0" :max="8" /></el-form-item><el-form-item label="图例 / 标签"><div class="chart-switches"><el-switch v-model="form.legend" active-text="图例" /><el-switch v-model="form.labels" active-text="标签" /></div></el-form-item></div><el-form-item label="无数据提示"><el-input v-model.trim="form.emptyText" maxlength="100" /></el-form-item></section>
        </aside>

        <main class="chart-live-preview"><header><div><span class="eyebrow">LIVE QUERY PREVIEW</span><h3>{{ form.title || form.name || '未命名图表' }}</h3><p>参数变化后自动取消旧请求并重新执行当前查询计划。</p></div><div class="preview-meta"><el-tag v-if="previewResult" type="success">{{ previewResult.rowCount }} 行 · {{ previewResult.executionMs }} ms</el-tag><el-tag v-if="previewResult?.cached" effect="plain">缓存命中</el-tag><el-button size="small" :loading="previewLoading" @click="runPreview">刷新</el-button></div></header><div class="chart-preview-frame"><ChartPreview :spec="chartSpec" :result="previewResult" :loading="previewLoading" :error="previewError" @retry="runPreview" /></div><el-alert v-if="localIssues.length" type="warning" :title="localIssues[0]" show-icon :closable="false" /><div class="chart-query-summary"><span>数据集版本 <strong>{{ form.datasetVersionId || '—' }}</strong></span><span>指标 <strong>{{ form.metricVersionIds.length }}</strong></span><span>维度 <strong>{{ queryPlan.dimensionFieldIds.length }}</strong></span><span>限制 <strong>{{ form.limit.toLocaleString('zh-CN') }}</strong></span></div></main>
      </div>
      <template #footer><div class="wizard-footer"><el-button @click="designerOpen = false">取消</el-button><span /><el-button :loading="previewLoading" @click="runPreview">重新预览</el-button><el-button type="primary" :loading="saving" :disabled="Boolean(localIssues.length)" @click="saveDraft">保存草稿</el-button></div></template>
    </CommonDialog>

    <CommonDialog v-model="validationOpen" :title="`图表校验 · ${validationChart?.name ?? ''}`" width="720px"><el-alert v-if="validationResult" :type="validationResult.valid ? 'success' : 'error'" :title="validationResult.valid ? '图表配置和查询计划校验通过，可以发布。' : `发现 ${validationResult.errors.length} 个阻断问题`" show-icon :closable="false" /><div v-if="validationResult && !validationResult.valid" class="validation-issues"><button v-for="item in validationResult.errors" :key="`${item.path}-${item.code}`" type="button" @click="locateValidation(item)"><el-tag type="danger">{{ item.code }}</el-tag><span><strong>{{ item.path }}</strong><small>{{ item.message }}</small></span><span>定位修改 →</span></button></div><template #footer><el-button type="primary" @click="validationOpen = false">关闭</el-button></template></CommonDialog>

    <CommonDialog v-model="versionsOpen" :title="`历史版本 · ${versionsChart?.name ?? ''}`" width="900px"><el-table :data="selectedVersions" stripe><el-table-column label="版本" width="80"><template #default="{ row }">V{{ row.versionNo }}</template></el-table-column><el-table-column label="类型" width="120"><template #default="{ row }">{{ typeLabel(row.chartSpec.type) }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></template></el-table-column><el-table-column label="指标 / 维度" width="130"><template #default="{ row }">{{ row.queryPlan.metricVersionIds.length }} / {{ row.queryPlan.dimensionFieldIds.length }}</template></el-table-column><el-table-column label="查询限制" width="100"><template #default="{ row }">{{ row.queryPlan.limit }}</template></el-table-column><el-table-column label="发布时间" min-width="170"><template #default="{ row }">{{ row.publishedAt ? formatDate(row.publishedAt) : '未发布' }}</template></el-table-column></el-table><template #footer><el-button type="primary" @click="versionsOpen = false">关闭</el-button></template></CommonDialog>
  </div>
</template>

<script setup lang="ts">
import { optionalResourceId } from '../api/resource-id'
import type { Id } from '../api/resource-id'
import { computed, markRaw, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { DataAnalysis, DataLine, Grid, Histogram, List, PieChart, Plus, TrendCharts } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChartPreview from '../components/common/ChartPreview.vue'
import CommonDialog from '../components/common/CommonDialog.vue'
import UnifiedState from '../components/common/UnifiedState.vue'
import { dashboardApi, queryApi, semanticApi } from '../api/runtime'
import { ApiClientError } from '../api/client'
import type { Chart, ChartSpec, ChartType, ChartVersion } from '../dashboard/types'
import type { QueryPlan, QueryResult } from '../query/types'
import type { Dataset, DatasetVersion, Metric, MetricVersion } from '../semantic/types'
import { useConfirm } from '../composables/useConfirm'
import { useAppStore } from '../stores/app'

interface DatasetOption { dataset: Dataset; version: DatasetVersion }
interface MetricOption { metric: Metric; version: MetricVersion }
interface ChartRow extends Chart { activeVersion?: ChartVersion; type: ChartType; sourceName: string; versionText: string }
interface ValidationIssue { path: string; code: string; message: string }
interface ValidationData { valid: boolean; errors: ValidationIssue[] }

const app = useAppStore()
const { confirm } = useConfirm()
const charts = ref<Chart[]>([])
const datasets = ref<Dataset[]>([])
const metrics = ref<Metric[]>([])
const versionsByChart = ref<Record<Id, ChartVersion[]>>({})
const versionsByDataset = ref<Record<Id, DatasetVersion[]>>({})
const versionsByMetric = ref<Record<Id, MetricVersion[]>>({})
const loading = ref(true)
const loadError = ref('')
const filters = reactive({ keyword: '', type: '' as ChartType | '', status: '' })

const designerOpen = ref(false)
const editingChart = ref<Chart>()
const saving = ref(false)
const previewLoading = ref(false)
const previewError = ref('')
const previewResult = ref<QueryResult>()
const form = reactive({ name: '', code: '', datasetVersionId: undefined as Id | undefined, type: 'BAR' as ChartType,
  metricVersionIds: [] as Id[], categoryFieldId: undefined as Id | undefined, seriesFieldId: undefined as Id | undefined,
  detailFieldIds: [] as Id[], sortTarget: '', sortDirection: 'DESC' as 'ASC' | 'DESC', limit: 500,
  title: '', xAxisTitle: '', yAxisTitle: '', unit: '', valueFormat: 'NUMBER' as 'NUMBER' | 'CURRENCY' | 'PERCENT',
  decimalPlaces: 2, legend: true, labels: true, emptyText: '当前筛选下暂无数据' })
let previewTimer: ReturnType<typeof setTimeout> | undefined
let previewController: AbortController | undefined
let previewSequence = 0

const validationOpen = ref(false)
const validationChart = ref<Chart>()
const validationResult = ref<ValidationData>()
const versionsOpen = ref(false)
const versionsChart = ref<Chart>()

const chartTypes = [
  { value: 'KPI' as const, label: '指标卡', icon: markRaw(DataAnalysis) },
  { value: 'BAR' as const, label: '柱状图', icon: markRaw(Histogram) },
  { value: 'LINE' as const, label: '折线图', icon: markRaw(TrendCharts) },
  { value: 'TABLE' as const, label: '明细表', icon: markRaw(List) },
  { value: 'PIE' as const, label: '饼图', icon: markRaw(PieChart) },
  { value: 'STACKED_BAR' as const, label: '堆叠图', icon: markRaw(DataLine) },
  { value: 'PIVOT_TABLE' as const, label: '透视表', icon: markRaw(Grid) },
]
const workspaceName = computed(() => app.workspaces.find(item => item.id === app.workspaceId)?.workspaceName ?? '当前工作空间')
const publishedCount = computed(() => charts.value.filter(item => item.status === 'PUBLISHED').length)
const datasetOptions = computed<DatasetOption[]>(() => datasets.value.flatMap(dataset => (versionsByDataset.value[dataset.id] ?? []).filter(version => version.status === 'PUBLISHED').map(version => ({ dataset, version }))))
const selectedDataset = computed(() => datasetOptions.value.find(item => item.version.id === form.datasetVersionId))
const dimensionFields = computed(() => selectedDataset.value?.version.fields.filter(field => field.role === 'DIMENSION') ?? [])
const metricOptions = computed<MetricOption[]>(() => metrics.value.flatMap(metric => (versionsByMetric.value[metric.id] ?? []).filter(version => version.status === 'PUBLISHED').map(version => ({ metric, version }))))
const availableMetricOptions = computed(() => metricOptions.value.filter(item => item.version.datasetVersionId === form.datasetVersionId))
const selectedMetricOptions = computed(() => metricOptions.value.filter(item => form.metricVersionIds.includes(item.version.id)))
const needsCategory = computed(() => ['BAR', 'LINE', 'PIE', 'STACKED_BAR', 'PIVOT_TABLE'].includes(form.type))
const singleMetricType = computed(() => ['KPI', 'PIE', 'PIVOT_TABLE'].includes(form.type))
const selectedDimensionFields = computed(() => dimensionFields.value.filter(field => queryPlan.value.dimensionFieldIds.includes(field.id)))
const queryPlan = computed<QueryPlan>(() => {
  const dimensions = form.type === 'TABLE'
    ? form.detailFieldIds
    : form.type === 'PIVOT_TABLE'
      ? [form.categoryFieldId, form.seriesFieldId]
      : form.type === 'KPI'
        ? []
        : [form.categoryFieldId]
  const dimensionFieldIds = [...new Set(dimensions.filter((id): id is Id => Boolean(id)))]
  const [targetType, rawTarget] = form.sortTarget.split(':')
  const targetId = optionalResourceId(rawTarget)
  return { datasetVersionId: form.datasetVersionId ?? '', metricVersionIds: [...form.metricVersionIds], dimensionFieldIds,
    filters: [], sort: targetId && (targetType === 'FIELD' || targetType === 'METRIC') ? [{ targetType, targetId, direction: form.sortDirection }] : [], limit: form.limit }
})
const chartSpec = computed<ChartSpec>(() => ({ type: form.type, title: form.title || form.name,
  ...(needsCategory.value && form.categoryFieldId ? { categoryField: `dimension_${form.categoryFieldId}`, interactionFieldId: form.categoryFieldId } : {}),
  ...(form.type === 'PIVOT_TABLE' && form.seriesFieldId ? { seriesNameField: `dimension_${form.seriesFieldId}` } : {}),
  valueFields: form.metricVersionIds.map(id => `metric_${id}`), valueFormat: form.valueFormat, legend: form.legend,
  labels: form.labels, emptyText: form.emptyText,
  detailColumns: form.type === 'TABLE' ? [...form.detailFieldIds.map(id => `dimension_${id}`), ...form.metricVersionIds.map(id => `metric_${id}`)] : [],
  ...(form.xAxisTitle ? { xAxisTitle: form.xAxisTitle } : {}), ...(form.yAxisTitle ? { yAxisTitle: form.yAxisTitle } : {}),
  ...(form.unit ? { unit: form.unit } : {}), decimalPlaces: form.decimalPlaces }))
const localIssues = computed(() => {
  const issues: string[] = []
  if (!form.name.trim()) issues.push('请填写图表名称')
  if (!/^[a-z][a-z0-9_]{1,63}$/.test(form.code)) issues.push('图表编码须由小写字母开头，并只包含字母、数字和下划线')
  if (!selectedDataset.value) issues.push('请选择已发布数据集版本')
  if (!form.metricVersionIds.length) issues.push('请至少选择一个已发布指标')
  if (singleMetricType.value && form.metricVersionIds.length !== 1) issues.push(`${typeLabel(form.type)}必须且只能选择一个指标`)
  if (needsCategory.value && !form.categoryFieldId) issues.push('请选择分类维度')
  if (form.type === 'PIVOT_TABLE' && (!form.seriesFieldId || form.seriesFieldId === form.categoryFieldId)) issues.push('透视表必须选择不同的行维度和列维度')
  return issues
})
const chartRows = computed<ChartRow[]>(() => charts.value.map(chart => {
  const versions = versionsByChart.value[chart.id] ?? []
  const active = versions.find(item => item.status === 'DRAFT') ?? versions.find(item => item.id === chart.currentVersionId) ?? versions.at(-1)
  const source = datasetOptions.value.find(item => item.version.id === active?.datasetVersionId)
  return { ...chart, ...(active ? { activeVersion: active } : {}), type: active?.chartSpec.type ?? 'BAR', sourceName: source ? `${source.dataset.name} · V${source.version.versionNo}` : '版本不可用', versionText: active ? `V${active.versionNo}` : '—' }
}))
const filteredRows = computed(() => chartRows.value.filter(row => (!filters.keyword || `${row.name} ${row.code}`.toLowerCase().includes(filters.keyword.toLowerCase())) && (!filters.type || row.type === filters.type) && (!filters.status || row.status === filters.status)))
const selectedVersions = computed(() => versionsChart.value ? versionsByChart.value[versionsChart.value.id] ?? [] : [])

async function load(): Promise<void> {
  loading.value = true; loadError.value = ''
  const [chartResult, datasetResult, metricResult] = await Promise.allSettled([dashboardApi.listCharts(app.workspaceId), semanticApi.datasets(app.workspaceId), semanticApi.metrics(app.workspaceId)])
  if (chartResult.status === 'fulfilled') charts.value = chartResult.value
  if (datasetResult.status === 'fulfilled') datasets.value = datasetResult.value
  if (metricResult.status === 'fulfilled') metrics.value = metricResult.value
  const errors = [chartResult, datasetResult, metricResult].filter(item => item.status === 'rejected').map(item => (item as PromiseRejectedResult).reason)
  const [chartResults, datasetResults, metricResults] = await Promise.all([Promise.allSettled(charts.value.map(item => dashboardApi.listChartVersions(item.id))), Promise.allSettled(datasets.value.map(item => semanticApi.datasetVersions(item.id))), Promise.allSettled(metrics.value.map(item => semanticApi.metricVersions(item.id)))])
  versionsByChart.value = Object.fromEntries(charts.value.map((item, index) => [item.id, settledArray(chartResults[index], errors)]))
  versionsByDataset.value = Object.fromEntries(datasets.value.map((item, index) => [item.id, settledArray(datasetResults[index], errors)]))
  versionsByMetric.value = Object.fromEntries(metrics.value.map((item, index) => [item.id, settledArray(metricResults[index], errors)]))
  if (errors.length) loadError.value = errors[0] instanceof Error ? errors[0].message : '部分图表信息加载失败'
  loading.value = false
}

function openCreate(): void {
  editingChart.value = undefined
  Object.assign(form, { name: '', code: '', datasetVersionId: datasetOptions.value[0]?.version.id, type: 'BAR', metricVersionIds: [], categoryFieldId: undefined, seriesFieldId: undefined, detailFieldIds: [], sortTarget: '', sortDirection: 'DESC', limit: 500, title: '', xAxisTitle: '', yAxisTitle: '', unit: '', valueFormat: 'NUMBER', decimalPlaces: 2, legend: true, labels: true, emptyText: '当前筛选下暂无数据' })
  applyDataset(); previewResult.value = undefined; previewError.value = ''; designerOpen.value = true
}

async function openEdit(row: ChartRow): Promise<void> {
  const chart = charts.value.find(item => item.id === row.id); if (!chart) return
  const detail = await dashboardApi.getChart(chart.id); const version = detail.draft ?? detail.current
  if (!version) { ElMessage.warning('图表没有可编辑版本'); return }
  editingChart.value = chart
  const spec = version.chartSpec; const sort = version.queryPlan.sort[0]
  Object.assign(form, { name: chart.name, code: chart.code, datasetVersionId: version.datasetVersionId, type: spec.type,
    metricVersionIds: [...version.queryPlan.metricVersionIds], categoryFieldId: keyId(spec.categoryField), seriesFieldId: keyId(spec.seriesNameField),
    detailFieldIds: (spec.detailColumns ?? []).filter(key => key.startsWith('dimension_')).map(keyId).filter((id): id is Id => Boolean(id)),
    sortTarget: sort ? `${sort.targetType}:${sort.targetId}` : '', sortDirection: sort?.direction ?? 'DESC', limit: version.queryPlan.limit,
    title: spec.title ?? '', xAxisTitle: spec.xAxisTitle ?? '', yAxisTitle: spec.yAxisTitle ?? '', unit: spec.unit ?? '',
    valueFormat: spec.valueFormat ?? 'NUMBER', decimalPlaces: spec.decimalPlaces ?? 2, legend: spec.legend !== false, labels: spec.labels !== false,
    emptyText: spec.emptyText ?? '当前筛选下暂无数据' })
  previewResult.value = undefined; previewError.value = ''; designerOpen.value = true; schedulePreview()
}

function applyDataset(): void {
  form.metricVersionIds = []
  form.categoryFieldId = dimensionFields.value[0]?.id
  form.seriesFieldId = dimensionFields.value[1]?.id
  form.detailFieldIds = dimensionFields.value.slice(0, 4).map(item => item.id)
  form.sortTarget = ''
}
function selectType(type: ChartType): void {
  form.type = type
  form.sortTarget = ''
  if (['KPI', 'PIE', 'PIVOT_TABLE'].includes(type) && form.metricVersionIds.length > 1) {
    form.metricVersionIds = form.metricVersionIds.slice(0, 1)
  }
}

function schedulePreview(): void { if (!designerOpen.value) return; if (previewTimer) clearTimeout(previewTimer); previewTimer = setTimeout(() => void runPreview(), 450) }
async function runPreview(): Promise<void> {
  if (localIssues.value.length) { previewResult.value = undefined; previewError.value = ''; return }
  const sequence = ++previewSequence
  previewController?.abort(); previewController = new AbortController(); previewLoading.value = true; previewError.value = ''
  try {
    const result = await queryApi.execute(queryPlan.value, previewController.signal, true)
    if (sequence === previewSequence) previewResult.value = result
  } catch (error) {
    if (sequence === previewSequence && !(error instanceof ApiClientError && error.code === 'REQUEST_ABORTED')) {
      previewResult.value = undefined
      previewError.value = error instanceof Error ? error.message : '图表查询失败'
    }
  } finally {
    if (sequence === previewSequence) previewLoading.value = false
  }
}

async function saveDraft(): Promise<void> {
  if (localIssues.value.length || !form.datasetVersionId) return
  saving.value = true
  try {
    if (editingChart.value) await dashboardApi.updateChart(editingChart.value.id, { name: form.name, datasetVersionId: form.datasetVersionId, queryPlan: queryPlan.value, chartSpec: chartSpec.value, lockVersion: editingChart.value.lockVersion })
    else await dashboardApi.createChart({ workspaceId: app.workspaceId, code: form.code, name: form.name, datasetVersionId: form.datasetVersionId, queryPlan: queryPlan.value, chartSpec: chartSpec.value })
    designerOpen.value = false; await load(); ElMessage.success('图表草稿已保存')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '图表保存失败') }
  finally { saving.value = false }
}

async function validateChart(row: ChartRow): Promise<void> {
  const chart = charts.value.find(item => item.id === row.id); if (!chart) return
  validationChart.value = chart
  try { validationResult.value = await dashboardApi.validateChart(chart.id) as ValidationData; validationOpen.value = true }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '图表校验失败') }
}
async function locateValidation(_item: ValidationIssue): Promise<void> { const row = chartRows.value.find(item => item.id === validationChart.value?.id); if (!row) return; validationOpen.value = false; await openEdit(row) }
async function publishChart(row: ChartRow): Promise<void> {
  const chart = charts.value.find(item => item.id === row.id); if (!chart || !await confirm(`发布“${chart.name}”后，该图表版本的查询计划和展示配置将不可变。`, { title: '发布图表版本', confirmText: '确认发布' })) return
  try { const version = await dashboardApi.publishChart(chart.id); ElMessage.success(`图表 V${version.versionNo} 已发布`); await load() }
  catch (error) { await load(); ElMessage.error(error instanceof Error ? `${error.message}，列表已刷新` : '发布失败') }
}
async function showVersions(row: ChartRow): Promise<void> { const chart = charts.value.find(item => item.id === row.id); if (!chart) return; versionsByChart.value[chart.id] = await dashboardApi.listChartVersions(chart.id); versionsChart.value = chart; versionsOpen.value = true }

function typeLabel(value: ChartType): string { return chartTypes.find(item => item.value === value)?.label ?? value }
function keyId(value?: string): Id | undefined { if (!value) return undefined; return optionalResourceId(value.replace(/^dimension_/, '').replace(/^metric_/, '')) }
function formatDate(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) }
function settledArray<T>(result: PromiseSettledResult<T[]> | undefined, errors: unknown[]): T[] { if (result?.status === 'fulfilled') return result.value; if (result?.status === 'rejected') errors.push(result.reason); return [] }

watch(() => JSON.stringify({ open: designerOpen.value, dataset: form.datasetVersionId, type: form.type, metrics: form.metricVersionIds, category: form.categoryFieldId, series: form.seriesFieldId, details: form.detailFieldIds, sort: form.sortTarget, direction: form.sortDirection, limit: form.limit, spec: chartSpec.value }), schedulePreview)
onBeforeUnmount(() => { if (previewTimer) clearTimeout(previewTimer); previewController?.abort() })
void load()
</script>
