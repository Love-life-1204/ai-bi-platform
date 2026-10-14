<template>
  <div class="metric-page">
    <section class="metric-hero">
      <div><span class="eyebrow">GOVERNED METRICS</span><h2>指标管理</h2><p>用受控公式定义业务口径，在发布前检查字段、权限样例、币种与依赖影响。</p></div>
      <div class="metric-stats"><span><strong>{{ metrics.length }}</strong><small>指标</small></span><span><strong>{{ publishedCount }}</strong><small>已发布</small></span><span><strong>{{ validatedCount }}</strong><small>待发布</small></span></div>
    </section>

    <section class="workbench-card metric-list-card">
      <header class="metric-card-heading">
        <div><span class="eyebrow">CURRENT WORKSPACE</span><h3>{{ workspaceName }}</h3><p>列表只展示当前工作空间内有权读取的指标。</p></div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建指标</el-button>
      </header>
      <div class="metric-filter-bar">
        <el-input v-model="filters.keyword" clearable placeholder="搜索指标名称或编码" />
        <el-select v-model="filters.datasetId" clearable placeholder="全部数据集"><el-option v-for="item in datasets" :key="item.id" :label="item.name" :value="item.id" /></el-select>
        <el-select v-model="filters.status" clearable placeholder="全部状态"><el-option label="草稿" value="DRAFT" /><el-option label="已校验" value="VALIDATED" /><el-option label="已发布" value="PUBLISHED" /></el-select>
        <el-select v-model="filters.ownerId" clearable placeholder="全部负责人"><el-option v-for="member in members" :key="member.id" :label="member.memberName" :value="member.id" /></el-select>
      </div>
      <UnifiedState v-if="loading" state="loading" description="正在加载指标、数据集版本与负责人。" />
      <UnifiedState v-else-if="loadError && !metrics.length" state="error" :description="loadError" retryable @retry="load" />
      <template v-else>
        <el-alert v-if="loadError" type="warning" :title="loadError" show-icon :closable="false" />
        <UnifiedState v-if="!filteredRows.length" state="empty" title="暂无符合条件的指标" description="请调整筛选条件，或基于已发布数据集创建第一个业务指标。" action-text="新建指标" @action="openCreate" />
        <el-table v-else :data="filteredRows" stripe>
          <el-table-column label="指标" min-width="185"><template #default="{ row }"><strong>{{ row.name }}</strong><small class="table-subtext">{{ row.code }} · {{ row.unit }}</small></template></el-table-column>
          <el-table-column label="来源数据集" min-width="190"><template #default="{ row }">{{ row.datasetName }}<small class="table-subtext">{{ row.datasetVersion }}</small></template></el-table-column>
          <el-table-column label="公式口径" min-width="240" show-overflow-tooltip><template #default="{ row }">{{ row.formulaText }}</template></el-table-column>
          <el-table-column label="负责人" width="130" prop="ownerName" />
          <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusTone(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="350" align="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑草稿</el-button><el-button link @click="trialExisting(row)">试算</el-button><el-button link type="primary" @click="validateMetric(row)">校验</el-button><el-button link @click="showVersions(row)">版本/影响</el-button><el-button link type="success" :disabled="row.status !== 'VALIDATED'" @click="publishMetric(row)">发布</el-button></template></el-table-column>
        </el-table>
      </template>
    </section>

    <CommonDialog v-model="wizardOpen" :title="editingMetric ? `编辑指标 · ${editingMetric.name}` : '创建业务指标'" width="1080px">
      <nav class="metric-wizard-steps" aria-label="指标创建步骤"><button v-for="(step, index) in wizardSteps" :key="step" type="button" :class="{ active: wizardStep === index, done: wizardStep > index }" @click="goToStep(index)"><span>{{ index + 1 }}</span>{{ step }}</button></nav>

      <section v-if="wizardStep === 0" class="metric-wizard-panel">
        <div class="wizard-intro"><span class="eyebrow">IDENTITY & SOURCE</span><h3>指标信息与来源版本</h3><p>指标必须固定引用当前工作空间内一个已发布的数据集版本。</p></div>
        <div class="metric-form-grid">
          <el-form-item label="指标名称"><el-input v-model.trim="metricForm.name" maxlength="200" placeholder="例如：净销售额" /></el-form-item>
          <el-form-item label="指标编码"><el-input v-model.trim="metricForm.code" :disabled="Boolean(editingMetric)" maxlength="64" placeholder="net_sales" /></el-form-item>
          <el-form-item label="单位"><el-input v-model.trim="metricForm.unit" maxlength="32" placeholder="CNY、%、单" /></el-form-item>
          <el-form-item label="已发布数据集版本" class="wide"><el-select v-model="metricForm.datasetVersionId" filterable placeholder="选择数据集 / 版本" @change="applyDataset"><el-option v-for="option in publishedDatasetOptions" :key="option.version.id" :label="`${option.dataset.name} · V${option.version.versionNo}`" :value="option.version.id" /></el-select></el-form-item>
        </div>
        <UnifiedState v-if="!publishedDatasetOptions.length" state="empty" title="没有可用的已发布数据集" description="请先完成数据集校验和发布，再创建指标。" action-text="前往数据集" @action="router.push('/semantic')" />
        <div v-else-if="selectedDatasetOption" class="metric-source-summary"><div><strong>{{ selectedDatasetOption.dataset.name }} · V{{ selectedDatasetOption.version.versionNo }}</strong><small>{{ selectedDatasetOption.version.fields.length }} 个字段，其中 {{ measureFields.length }} 个度量、{{ dimensionFields.length }} 个维度</small></div><el-tag type="success">已发布</el-tag></div>
      </section>

      <section v-else-if="wizardStep === 1" class="metric-wizard-panel">
        <div class="wizard-intro"><span class="eyebrow">CONTROLLED FORMULA</span><h3>受控公式构建</h3><p>只组合白名单聚合、四则运算和条件节点；页面不接受 SQL、函数文本或脚本。</p></div>
        <div class="formula-builder-grid">
          <article class="formula-block"><header><strong>主计算</strong><small>必填</small></header><el-form-item label="聚合方式"><el-select v-model="formula.aggregation"><el-option v-for="item in aggregations" :key="item" :label="aggregationLabel(item)" :value="item" /></el-select></el-form-item><el-form-item v-if="formula.aggregation !== 'COUNT'" label="计算字段"><el-select v-model="formula.fieldKey" filterable><el-option v-for="field in measureAndDimensionFields" :key="field.stableFieldKey" :label="field.businessName" :value="field.stableFieldKey"><span>{{ field.businessName }}</span><small class="option-meta">{{ field.dataType }} · {{ roleLabel(field.role) }}</small></el-option></el-select></el-form-item></article>
          <article class="formula-block"><header><strong>组合运算</strong><small>可选</small></header><el-form-item label="与第二计算组合"><el-select v-model="formula.combine"><el-option label="不组合" value="NONE" /><el-option label="相加" value="ADD" /><el-option label="相减" value="SUB" /><el-option label="相除" value="DIV" /></el-select></el-form-item><template v-if="formula.combine !== 'NONE'"><el-form-item label="第二聚合"><el-select v-model="formula.secondaryAggregation"><el-option v-for="item in aggregations" :key="item" :label="aggregationLabel(item)" :value="item" /></el-select></el-form-item><el-form-item v-if="formula.secondaryAggregation !== 'COUNT'" label="第二字段"><el-select v-model="formula.secondaryFieldKey" filterable><el-option v-for="field in measureAndDimensionFields" :key="field.stableFieldKey" :label="field.businessName" :value="field.stableFieldKey" /></el-select></el-form-item><el-checkbox v-if="formula.combine === 'DIV'" v-model="formula.protectZero">分母为零时返回“未定义”</el-checkbox></template></article>
          <article class="formula-block formula-filter"><header><strong>统计条件</strong><el-switch v-model="formula.filterEnabled" /></header><template v-if="formula.filterEnabled"><div class="formula-condition-row"><el-select v-model="formula.filterFieldKey" filterable placeholder="条件字段"><el-option v-for="field in dimensionFields" :key="field.stableFieldKey" :label="field.businessName" :value="field.stableFieldKey" /></el-select><el-select v-model="formula.filterOperator"><el-option v-for="item in comparisons" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-input v-model.trim="formula.filterValue" placeholder="条件值，例如 CANCELLED" /></div></template><p v-else>不添加行条件，使用权限范围内的全部样例行。</p></article>
        </div>
        <div class="formula-preview"><span>业务口径</span><strong>{{ currentFormulaSummary }}</strong><small>受控 AST：{{ compactAst }}</small></div>
        <el-alert v-if="formulaIssues.length" type="error" :title="formulaIssues[0]?.message" show-icon :closable="false" />
      </section>

      <section v-else-if="wizardStep === 2" class="metric-wizard-panel">
        <div class="wizard-intro"><span class="eyebrow">BUSINESS GOVERNANCE</span><h3>时间、币种与维度范围</h3><p>时间固定使用 [开始, 结束)；跨币种必须指定目标币种和批准汇率来源。</p></div>
        <div class="metric-form-grid">
          <el-form-item label="业务时间字段"><el-select v-model="metricForm.timeField" filterable><el-option v-for="field in timeFields" :key="field.stableFieldKey" :label="field.businessName" :value="field.stableFieldKey" /></el-select></el-form-item>
          <el-form-item label="业务时区"><el-select v-model="metricForm.timezone" filterable allow-create><el-option label="Asia/Shanghai" value="Asia/Shanghai" /><el-option label="UTC" value="UTC" /><el-option label="Asia/Tokyo" value="Asia/Tokyo" /><el-option label="America/New_York" value="America/New_York" /></el-select></el-form-item>
          <el-form-item label="精度"><el-input-number v-model="metricForm.precisionScale" :min="0" :max="8" /></el-form-item>
          <el-form-item label="币种模式"><el-select v-model="metricForm.currencyMode"><el-option label="单币种" value="SINGLE" /><el-option label="固定币种" value="FIXED" /><el-option label="跨币种换算" value="CONVERT" /></el-select></el-form-item>
          <el-form-item label="币种字段"><el-select v-model="metricForm.currencyField" filterable><el-option v-for="field in dimensionFields" :key="field.stableFieldKey" :label="field.businessName" :value="field.stableFieldKey" /></el-select></el-form-item>
          <el-form-item v-if="metricForm.currencyMode !== 'SINGLE'" label="目标币种"><el-input v-model.trim="metricForm.targetCurrency" maxlength="3" placeholder="CNY" /></el-form-item>
          <el-form-item v-if="metricForm.currencyMode === 'CONVERT'" label="批准汇率来源"><el-input v-model.trim="metricForm.rateSource" maxlength="100" placeholder="例如 enterprise_daily_rate" /></el-form-item>
          <el-form-item label="允许下钻维度" class="wide"><el-select v-model="metricForm.allowedDimensions" multiple filterable collapse-tags><el-option v-for="field in dimensionFields" :key="field.stableFieldKey" :label="field.businessName" :value="field.stableFieldKey" /></el-select></el-form-item>
        </div>
        <div class="governance-note"><strong>[start, end)</strong><span>开始时间包含，结束时间不包含，避免相邻统计周期重复计数。</span></div>
      </section>

      <section v-else class="metric-wizard-panel">
        <div class="wizard-intro"><span class="eyebrow">REVIEW & TRIAL</span><h3>确认口径并试算</h3><p>保存草稿前，可使用当前成员有权查看的数据集样例进行本地受控试算。</p></div>
        <div class="metric-review-grid"><div><small>指标</small><strong>{{ metricForm.name || '—' }}</strong><span>{{ metricForm.code }} · {{ metricForm.unit }}</span></div><div><small>来源</small><strong>{{ selectedDatasetOption?.dataset.name ?? '—' }}</strong><span>V{{ selectedDatasetOption?.version.versionNo ?? '—' }}</span></div><div><small>时间</small><strong>{{ metricForm.timezone }}</strong><span>{{ fieldName(metricForm.timeField) }}</span></div><div><small>币种 / 精度</small><strong>{{ metricForm.currencyMode }}</strong><span>{{ metricForm.targetCurrency || metricForm.unit }} · {{ metricForm.precisionScale }} 位</span></div></div>
        <div class="formula-preview review"><span>最终口径</span><strong>{{ currentFormulaSummary }}</strong></div>
        <div class="trial-inline"><div><strong>发布前试算</strong><p>权限预览数据不会绕过后端行列权限；跨币种不使用假汇率。</p></div><el-button :loading="trialLoading" @click="runTrial">执行试算</el-button></div>
        <el-alert v-if="wizardIssues.length" type="error" :title="`还有 ${wizardIssues.length} 个问题需要修复`" show-icon :closable="false"><ul><li v-for="item in wizardIssues" :key="`${item.path}-${item.code}`">{{ item.message }}</li></ul></el-alert>
      </section>

      <template #footer><div class="wizard-footer"><el-button @click="wizardOpen = false">取消</el-button><span /><el-button v-if="wizardStep" @click="wizardStep--">上一步</el-button><el-button v-if="wizardStep < 3" type="primary" @click="nextStep">下一步</el-button><el-button v-else type="primary" :loading="saving" :disabled="Boolean(wizardIssues.length)" @click="saveDraft">保存草稿</el-button></div></template>
    </CommonDialog>

    <CommonDialog v-model="trialOpen" :title="`指标试算 · ${trialMetricName}`" width="720px">
      <UnifiedState v-if="trialLoading" state="loading" description="正在获取当前成员有权查看的样例数据。" />
      <UnifiedState v-else-if="trialError" state="error" :description="trialError" />
      <template v-else-if="trialResult"><div class="trial-source"><span>当前数据：{{ trialSource }}</span><small>沙盒数据只验证口径边界，不代表真实业务结果。</small></div><div class="trial-result" :class="trialResult.status.toLowerCase()"><span>{{ trialStatusLabel(trialResult.status) }}</span><strong>{{ trialResult.value ?? '—' }} <small>{{ trialResult.currency ?? '' }}</small></strong><div><span>样例 {{ trialResult.rowCount }} 行</span><span>条件命中 {{ trialResult.matchingRows }} 行</span><span>缺失值 {{ trialResult.missingValues }} 行</span></div></div><el-alert v-for="note in trialResult.notes" :key="note" :type="trialResult.status === 'DEFINED' ? 'warning' : 'info'" :title="note" show-icon :closable="false" /><div class="trial-scenarios"><span>典型场景沙盒</span><el-button size="small" @click="runScenario('REFUND_CANCEL')">退款与取消</el-button><el-button size="small" @click="runScenario('MISSING')">缺失值</el-button><el-button size="small" @click="runScenario('ZERO')">零分母</el-button><el-button size="small" @click="runScenario('CROSS_CURRENCY')">跨币种</el-button></div></template>
      <template #footer><el-button type="primary" @click="trialOpen = false">关闭</el-button></template>
    </CommonDialog>

    <CommonDialog v-model="validationOpen" :title="`公式校验 · ${validationMetric?.name ?? ''}`" width="760px">
      <template v-if="validationResult"><el-alert :type="validationResult.valid ? 'success' : 'error'" :title="validationResult.valid ? '公式、时间、币种和依赖校验通过。' : `发现 ${validationResult.errors.length} 个阻断问题`" show-icon :closable="false" /><div class="validation-issues"><button v-for="item in [...validationResult.errors, ...validationResult.warnings]" :key="`${item.path}-${item.code}`" type="button" @click="locateMetricIssue(item)"><el-tag :type="validationResult.errors.includes(item) ? 'danger' : 'warning'">{{ item.code }}</el-tag><span><strong>{{ item.path }}</strong><small>{{ item.message }}</small></span><span>定位修改 →</span></button></div></template>
      <template #footer><el-button type="primary" @click="validationOpen = false">关闭</el-button></template>
    </CommonDialog>

    <CommonDialog v-model="versionsOpen" :title="`版本与依赖影响 · ${versionsMetric?.name ?? ''}`" width="930px">
      <div class="impact-grid"><article><small>引用的数据集</small><strong>{{ impactDataset }}</strong></article><article><small>直接依赖指标</small><strong>{{ outgoingDependencies.length }}</strong><span>{{ outgoingDependencies.join('、') || '无' }}</span></article><article><small>下游受影响指标</small><strong>{{ incomingDependencies.length }}</strong><span>{{ incomingDependencies.join('、') || '无' }}</span></article></div>
      <el-table :data="selectedMetricVersions" stripe><el-table-column label="版本" width="80"><template #default="{ row }">V{{ row.versionNo }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusTone(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column><el-table-column label="数据集版本" width="130"><template #default="{ row }">{{ datasetVersionName(row.datasetVersionId) }}</template></el-table-column><el-table-column label="维度 / 精度" width="130"><template #default="{ row }">{{ row.allowedDimensions.length }} / {{ row.precisionScale }}</template></el-table-column><el-table-column prop="definitionHash" label="定义哈希" min-width="190" show-overflow-tooltip /><el-table-column label="发布时间" width="170"><template #default="{ row }">{{ row.publishedAt ? formatDate(row.publishedAt) : '未发布' }}</template></el-table-column></el-table>
      <template #footer><el-button type="primary" @click="versionsOpen = false">关闭</el-button></template>
    </CommonDialog>
  </div>
</template>

<script setup lang="ts">
import type { Id } from '../../api/resource-id'
import { resourceId } from '../../api/resource-id'
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import CommonDialog from '../../components/common/CommonDialog.vue'
import UnifiedState from '../../components/common/UnifiedState.vue'
import { organizationApi, semanticApi } from '../../api/runtime'
import type { MemberContext } from '../../auth/types'
import { useConfirm } from '../../composables/useConfirm'
import { useAppStore } from '../../stores/app'
import { buildControlledFormula, formulaSummary, trialMetricFormula, validateFormulaDraft,
  type Aggregation, type FormulaNode, type MetricFormulaDraft, type MetricTrialResult } from '../../semantic/metric-formula'
import type { Dataset, DatasetField, DatasetVersion, Metric, MetricVersion, ValidationIssue, ValidationResult } from '../../semantic/types'

interface DatasetOption { dataset: Dataset; version: DatasetVersion }
interface MetricRow extends Metric { activeVersion?: MetricVersion; datasetId?: Id; datasetName: string; datasetVersion: string; formulaText: string; ownerName: string }

const app = useAppStore()
const router = useRouter()
const { confirm } = useConfirm()
const loading = ref(true)
const loadError = ref('')
const metrics = ref<Metric[]>([])
const datasets = ref<Dataset[]>([])
const members = ref<MemberContext[]>([])
const metricVersions = ref<Record<Id, MetricVersion[]>>({})
const datasetVersions = ref<Record<Id, DatasetVersion[]>>({})
const filters = reactive({ keyword: '', datasetId: undefined as Id | undefined, status: '', ownerId: undefined as Id | undefined })

const wizardOpen = ref(false)
const wizardStep = ref(0)
const editingMetric = ref<Metric>()
const saving = ref(false)
const metricForm = reactive({ name: '', code: '', unit: 'CNY', datasetVersionId: undefined as Id | undefined,
  precisionScale: 2, timeField: '', timezone: 'Asia/Shanghai', currencyMode: 'SINGLE' as 'SINGLE' | 'FIXED' | 'CONVERT',
  currencyField: '', targetCurrency: 'CNY', rateSource: '', allowedDimensions: [] as string[] })
const formula = reactive<MetricFormulaDraft>(emptyFormula())

const trialOpen = ref(false)
const trialLoading = ref(false)
const trialError = ref('')
const trialResult = ref<MetricTrialResult>()
const trialMetricName = ref('')
const trialSource = ref('权限过滤后的数据集预览')
const validationOpen = ref(false)
const validationMetric = ref<Metric>()
const validationResult = ref<ValidationResult>()
const versionsOpen = ref(false)
const versionsMetric = ref<Metric>()

const wizardSteps = ['基本信息', '受控公式', '业务规则', '确认与试算']
const aggregations: Aggregation[] = ['SUM', 'COUNT', 'COUNT_DISTINCT', 'AVG', 'MIN', 'MAX']
const comparisons = [{ value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' }, { value: 'GT', label: '大于' }, { value: 'GTE', label: '大于等于' }, { value: 'LT', label: '小于' }, { value: 'LTE', label: '小于等于' }] as const
const workspaceName = computed(() => app.workspaces.find(item => item.id === app.workspaceId)?.workspaceName ?? '当前工作空间')
const publishedCount = computed(() => metrics.value.filter(item => item.status === 'PUBLISHED').length)
const validatedCount = computed(() => metrics.value.filter(item => item.status === 'VALIDATED').length)
const publishedDatasetOptions = computed<DatasetOption[]>(() => datasets.value.flatMap(dataset => (datasetVersions.value[dataset.id] ?? []).filter(version => version.status === 'PUBLISHED').map(version => ({ dataset, version }))))
const selectedDatasetOption = computed(() => publishedDatasetOptions.value.find(option => option.version.id === metricForm.datasetVersionId))
const availableFields = computed(() => selectedDatasetOption.value?.version.fields ?? [])
const measureFields = computed(() => availableFields.value.filter(field => field.role === 'MEASURE'))
const dimensionFields = computed(() => availableFields.value.filter(field => field.role === 'DIMENSION'))
const measureAndDimensionFields = computed(() => availableFields.value)
const timeFields = computed(() => dimensionFields.value.filter(field => ['DATE', 'DATETIME'].includes(field.dataType)))
const formulaIssues = computed(() => validateFormulaDraft(formula, availableFields.value))
const currentFormula = computed(() => buildControlledFormula(formula, availableFields.value))
const currentFormulaSummary = computed(() => formulaSummary(formula, availableFields.value))
const compactAst = computed(() => JSON.stringify(currentFormula.value))
const wizardIssues = computed<ValidationIssue[]>(() => {
  const issues = [...formulaIssues.value]
  if (!metricForm.name.trim()) issues.push(issue('NAME_REQUIRED', 'name', '请填写指标名称'))
  if (!/^[a-z][a-z0-9_]{1,63}$/.test(metricForm.code)) issues.push(issue('CODE_INVALID', 'code', '指标编码须为小写字母开头的字母、数字或下划线'))
  if (!metricForm.unit.trim()) issues.push(issue('UNIT_REQUIRED', 'unit', '请填写指标单位'))
  if (!selectedDatasetOption.value) issues.push(issue('DATASET_REQUIRED', 'datasetVersionId', '请选择已发布的数据集版本'))
  if (!metricForm.timeField) issues.push(issue('TIME_FIELD_REQUIRED', 'timeSpec.eventTimeField', '请选择业务时间字段'))
  if (!metricForm.currencyField) issues.push(issue('CURRENCY_FIELD_REQUIRED', 'currencySpec.currencyField', '请选择币种字段'))
  if (metricForm.currencyMode !== 'SINGLE' && !metricForm.targetCurrency.trim()) issues.push(issue('TARGET_CURRENCY_REQUIRED', 'currencySpec.targetCurrency', '请填写目标币种'))
  if (metricForm.currencyMode === 'CONVERT' && !metricForm.rateSource.trim()) issues.push(issue('RATE_SOURCE_REQUIRED', 'currencySpec.rateSource', '跨币种必须填写批准汇率来源'))
  return issues
})
const metricRows = computed<MetricRow[]>(() => metrics.value.map(metric => {
  const versions = metricVersions.value[metric.id] ?? []
  const active = versions.find(version => ['DRAFT', 'VALIDATED'].includes(version.status)) ?? versions.find(version => version.id === metric.currentVersionId) ?? versions.at(-1)
  const datasetOption = publishedDatasetOptions.value.find(option => option.version.id === active?.datasetVersionId)
  return { ...metric, ...(active ? { activeVersion: active } : {}), ...(datasetOption ? { datasetId: datasetOption.dataset.id } : {}), datasetName: datasetOption?.dataset.name ?? '版本不可用', datasetVersion: active ? `V${datasetOption?.version.versionNo ?? '?'} · ID ${active.datasetVersionId}` : '尚无版本', formulaText: active ? summarizeAst(active.formulaAst, active, datasetOption?.version.fields ?? []) : '—', ownerName: memberName(metric.ownerMemberId) }
}))
const filteredRows = computed(() => metricRows.value.filter(row => {
  const keyword = filters.keyword.trim().toLowerCase()
  return (!keyword || `${row.name} ${row.code}`.toLowerCase().includes(keyword))
    && (!filters.datasetId || row.datasetId === filters.datasetId)
    && (!filters.status || row.status === filters.status)
    && (!filters.ownerId || row.ownerMemberId === filters.ownerId)
}))
const selectedMetricVersions = computed(() => versionsMetric.value ? metricVersions.value[versionsMetric.value.id] ?? [] : [])
const selectedImpactVersion = computed(() => selectedMetricVersions.value.find(item => ['DRAFT', 'VALIDATED'].includes(item.status)) ?? selectedMetricVersions.value.find(item => item.id === versionsMetric.value?.currentVersionId) ?? selectedMetricVersions.value.at(-1))
const impactDataset = computed(() => selectedImpactVersion.value ? datasetVersionName(selectedImpactVersion.value.datasetVersionId) : '—')
const outgoingDependencies = computed(() => (selectedImpactVersion.value?.dependencyVersionIds ?? []).map(metricVersionName))
const incomingDependencies = computed(() => {
  const ids = new Set(selectedMetricVersions.value.map(item => item.id))
  return metrics.value.filter(metric => metric.id !== versionsMetric.value?.id && (metricVersions.value[metric.id] ?? []).some(version => version.dependencyVersionIds.some(id => ids.has(id)))).map(item => item.name)
})

async function load(): Promise<void> {
  loading.value = true; loadError.value = ''
  const [metricResult, datasetResult, memberResult] = await Promise.allSettled([semanticApi.metrics(app.workspaceId), semanticApi.datasets(app.workspaceId), organizationApi.members(resourceId(app.tenantId))])
  if (metricResult.status === 'fulfilled') metrics.value = metricResult.value
  if (datasetResult.status === 'fulfilled') datasets.value = datasetResult.value
  if (memberResult.status === 'fulfilled') members.value = memberResult.value
  const errors = [metricResult, datasetResult, memberResult].filter(result => result.status === 'rejected').map(result => (result as PromiseRejectedResult).reason)
  const [metricVersionResults, datasetVersionResults] = await Promise.all([
    Promise.allSettled(metrics.value.map(item => semanticApi.metricVersions(item.id))),
    Promise.allSettled(datasets.value.map(item => semanticApi.datasetVersions(item.id))),
  ])
  metricVersions.value = Object.fromEntries(metrics.value.map((item, index) => [item.id, settledArray(metricVersionResults[index], errors)]))
  datasetVersions.value = Object.fromEntries(datasets.value.map((item, index) => [item.id, settledArray(datasetVersionResults[index], errors)]))
  if (errors.length) loadError.value = errors[0] instanceof Error ? errors[0].message : '部分指标信息加载失败'
  loading.value = false
}

function openCreate(): void {
  editingMetric.value = undefined; wizardStep.value = 0
  Object.assign(metricForm, { name: '', code: '', unit: 'CNY', datasetVersionId: publishedDatasetOptions.value[0]?.version.id,
    precisionScale: 2, timeField: '', timezone: 'Asia/Shanghai', currencyMode: 'SINGLE', currencyField: '', targetCurrency: 'CNY', rateSource: '', allowedDimensions: [] })
  Object.assign(formula, emptyFormula()); applyDataset(); wizardOpen.value = true
}

async function openEdit(row: MetricRow): Promise<void> {
  const metric = metrics.value.find(item => item.id === row.id); if (!metric) return
  editingMetric.value = metric; wizardStep.value = 0
  const detail = await semanticApi.metric(metric.id)
  const versions = metricVersions.value[metric.id] ?? []
  const version = detail.draft ?? versions.find(item => item.id === metric.currentVersionId) ?? versions.at(-1)
  if (!version) { ElMessage.warning('指标没有可编辑版本'); return }
  const time = asRecord(version.timeSpec); const currency = asRecord(version.currencySpec)
  Object.assign(metricForm, { name: metric.name, code: metric.code, unit: metric.unit, datasetVersionId: version.datasetVersionId,
    precisionScale: version.precisionScale, timeField: String(time.eventTimeField ?? ''), timezone: String(time.timezone ?? 'Asia/Shanghai'),
    currencyMode: String(currency.mode ?? 'SINGLE'), currencyField: String(currency.currencyField ?? ''), targetCurrency: String(currency.targetCurrency ?? metric.unit),
    rateSource: String(currency.rateSource ?? ''), allowedDimensions: [...version.allowedDimensions] })
  Object.assign(formula, parseFormula(version.formulaAst)); wizardOpen.value = true
}

function applyDataset(): void {
  const fields = availableFields.value
  formula.fieldKey = fields.find(item => item.role === 'MEASURE')?.stableFieldKey ?? fields[0]?.stableFieldKey ?? ''
  formula.secondaryFieldKey = fields.filter(item => item.role === 'MEASURE')[1]?.stableFieldKey ?? formula.fieldKey
  formula.filterFieldKey = fields.find(item => item.role === 'DIMENSION')?.stableFieldKey ?? ''
  metricForm.timeField = fields.find(item => ['DATE', 'DATETIME'].includes(item.dataType))?.stableFieldKey ?? ''
  metricForm.currencyField = fields.find(item => item.stableFieldKey.toLowerCase().includes('currency'))?.stableFieldKey ?? ''
  metricForm.allowedDimensions = fields.filter(item => item.role === 'DIMENSION').slice(0, 3).map(item => item.stableFieldKey)
}

function nextStep(): void {
  if (wizardStep.value === 0 && (!metricForm.name || !/^[a-z][a-z0-9_]{1,63}$/.test(metricForm.code) || !metricForm.unit || !selectedDatasetOption.value)) { ElMessage.warning('请完整填写指标信息并选择已发布数据集版本'); return }
  if (wizardStep.value === 1 && formulaIssues.value.length) { ElMessage.warning(formulaIssues.value[0]?.message); return }
  if (wizardStep.value === 2 && wizardIssues.value.some(item => item.path.startsWith('timeSpec') || item.path.startsWith('currencySpec'))) { ElMessage.warning('请完善时间和币种规则'); return }
  wizardStep.value = Math.min(3, wizardStep.value + 1)
}
function goToStep(index: number): void { if (index <= wizardStep.value) wizardStep.value = index }

async function saveDraft(): Promise<void> {
  if (wizardIssues.value.length || !metricForm.datasetVersionId) return
  saving.value = true
  const input = { name: metricForm.name, unit: metricForm.unit, datasetVersionId: metricForm.datasetVersionId,
    formulaAst: currentFormula.value, timeSpec: { timezone: metricForm.timezone, eventTimeField: metricForm.timeField, startInclusive: true, endExclusive: true },
    currencySpec: { mode: metricForm.currencyMode, currencyField: metricForm.currencyField, ...(metricForm.currencyMode !== 'SINGLE' ? { targetCurrency: metricForm.targetCurrency } : {}), ...(metricForm.currencyMode === 'CONVERT' ? { rateSource: metricForm.rateSource } : {}) },
    allowedDimensions: metricForm.allowedDimensions, precisionScale: metricForm.precisionScale, dependencyVersionIds: [] }
  try {
    if (editingMetric.value) await semanticApi.updateMetric(editingMetric.value.id, { ...input, lockVersion: editingMetric.value.lockVersion })
    else await semanticApi.createMetric({ workspaceId: app.workspaceId, code: metricForm.code, ...input })
    wizardOpen.value = false; await load(); ElMessage.success('指标草稿已保存，请继续执行服务端校验')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '指标草稿保存失败') }
  finally { saving.value = false }
}

async function runTrial(): Promise<void> {
  const option = selectedDatasetOption.value
  if (!option) return
  trialOpen.value = true; trialLoading.value = true; trialError.value = ''; trialResult.value = undefined; trialMetricName.value = metricForm.name || '未命名指标'
  trialSource.value = '权限过滤后的数据集预览'
  try {
    const preview = await semanticApi.previewDataset(option.dataset.id)
    trialResult.value = trialMetricFormula(currentFormula.value, preview.rows, { precisionScale: metricForm.precisionScale,
      currencyMode: metricForm.currencyMode, currencyField: metricForm.currencyField, targetCurrency: metricForm.targetCurrency }, formula)
  } catch (error) { trialError.value = error instanceof Error ? error.message : '指标试算失败' }
  finally { trialLoading.value = false }
}

function runScenario(kind: 'REFUND_CANCEL' | 'MISSING' | 'ZERO' | 'CROSS_CURRENCY'): void {
  const activeValue = formula.filterOperator === 'EQ' ? formula.filterValue : 'PAID'
  const excludedValue = formula.filterValue || 'CANCELLED'
  const row = (primary: unknown, secondary: unknown, currency: string, condition = activeValue) => ({
    [formula.fieldKey]: primary,
    ...(formula.secondaryFieldKey ? { [formula.secondaryFieldKey]: secondary } : {}),
    ...(formula.filterFieldKey ? { [formula.filterFieldKey]: condition } : {}),
    ...(metricForm.currencyField ? { [metricForm.currencyField]: currency } : {}),
    ...(metricForm.timeField ? { [metricForm.timeField]: new Date().toISOString() } : {}),
  })
  const rows = kind === 'REFUND_CANCEL'
    ? [row(100, 0, 'CNY'), row(200, 50, 'CNY'), row(300, 0, 'CNY', excludedValue)]
    : kind === 'MISSING' ? [row(100, null, 'CNY')]
      : kind === 'ZERO' ? [row(100, 0, 'CNY')]
        : [row(100, 0, 'CNY'), row(10, 0, 'USD')]
  trialSource.value = ({ REFUND_CANCEL: '沙盒：退款与取消', MISSING: '沙盒：缺失值', ZERO: '沙盒：零分母', CROSS_CURRENCY: '沙盒：跨币种' })[kind]
  trialResult.value = trialMetricFormula(currentFormula.value, rows, { precisionScale: metricForm.precisionScale,
    currencyMode: metricForm.currencyMode, currencyField: metricForm.currencyField, targetCurrency: metricForm.targetCurrency }, formula)
}

async function trialExisting(row: MetricRow): Promise<void> {
  await openEdit(row); wizardOpen.value = false; await runTrial()
}

async function validateMetric(row: MetricRow): Promise<void> {
  const metric = metrics.value.find(item => item.id === row.id); if (!metric) return
  validationMetric.value = metric
  try { validationResult.value = await semanticApi.validateMetric(metric.id); validationOpen.value = true; await load() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '指标校验失败') }
}

async function locateMetricIssue(item: ValidationIssue): Promise<void> {
  const metric = validationMetric.value; if (!metric) return
  const row = metricRows.value.find(value => value.id === metric.id); if (!row) return
  validationOpen.value = false; await openEdit(row)
  wizardStep.value = item.path.startsWith('formula') || item.path.startsWith('dependency') ? 1 : item.path.startsWith('time') || item.path.startsWith('currency') || item.path.startsWith('allowed') ? 2 : 0
}

async function publishMetric(row: MetricRow): Promise<void> {
  const metric = metrics.value.find(item => item.id === row.id); if (!metric) return
  const ids = new Set((metricVersions.value[metric.id] ?? []).map(item => item.id))
  const downstream = metrics.value.filter(item => item.id !== metric.id && (metricVersions.value[item.id] ?? []).some(version => version.dependencyVersionIds.some(id => ids.has(id)))).length
  const message = `发布“${metric.name}”后，该口径版本将不可变。来源数据集：${row.datasetName}；直接依赖：${row.activeVersion?.dependencyVersionIds.length ?? 0}；下游受影响指标：${downstream}。`
  if (!await confirm(message, { title: '确认发布指标版本', confirmText: '确认发布' })) return
  try { const version = await semanticApi.publishMetric(metric.id); ElMessage.success(`指标 V${version.versionNo} 已发布`); await load() }
  catch (error) { await load(); ElMessage.error(error instanceof Error ? `${error.message}，列表已刷新` : '发布冲突，列表已刷新') }
}

async function showVersions(row: MetricRow): Promise<void> {
  const metric = metrics.value.find(item => item.id === row.id); if (!metric) return
  metricVersions.value[metric.id] = await semanticApi.metricVersions(metric.id); versionsMetric.value = metric; versionsOpen.value = true
}

function parseFormula(value: unknown): MetricFormulaDraft {
  const root = asFormula(value); const parsed = emptyFormula()
  let primary = root; let secondary: FormulaNode | undefined
  if (['ADD', 'SUB', 'DIV'].includes(root.op)) { parsed.combine = root.op as MetricFormulaDraft['combine']; primary = root.args?.[0] ?? primary; secondary = root.args?.[1]; if (secondary?.op === 'NULLIF') { parsed.protectZero = true; secondary = secondary.args?.[0] } }
  readAggregation(primary, parsed, false); if (secondary) readAggregation(secondary, parsed, true)
  return parsed
}
function readAggregation(node: FormulaNode, target: MetricFormulaDraft, secondary: boolean): void {
  if (!aggregations.includes(node.op as Aggregation)) return
  if (secondary) target.secondaryAggregation = node.op as Aggregation; else target.aggregation = node.op as Aggregation
  let value = node.args?.[0]
  if (value?.op === 'CASE') {
    const condition = value.args?.[0]; const field = condition?.args?.[0]; const literal = condition?.args?.[1]
    target.filterEnabled = true; target.filterOperator = (condition?.op ?? 'EQ') as MetricFormulaDraft['filterOperator']; target.filterFieldKey = field?.field ?? ''; target.filterValue = String(literal?.value ?? '')
    value = value.args?.[1]
  }
  if (secondary) target.secondaryFieldKey = value?.field ?? ''; else target.fieldKey = value?.field ?? ''
}

function summarizeAst(value: unknown, version: MetricVersion, fields: DatasetField[]): string {
  const parsed = parseFormula(value)
  const summary = formulaSummary(parsed, fields)
  return summary.includes('undefined') ? `${asFormula(value).op} · ${version.allowedDimensions.length} 个维度` : summary
}
function emptyFormula(): MetricFormulaDraft { return { aggregation: 'SUM', fieldKey: '', combine: 'NONE', secondaryAggregation: 'SUM', secondaryFieldKey: '', filterEnabled: false, filterFieldKey: '', filterOperator: 'EQ', filterValue: '', protectZero: true } }
function asFormula(value: unknown): FormulaNode { return typeof value === 'object' && value !== null && 'op' in value ? value as FormulaNode : { op: 'LITERAL', value: null } }
function asRecord(value: unknown): Record<string, unknown> { return typeof value === 'object' && value !== null ? value as Record<string, unknown> : {} }
function settledArray<T>(result: PromiseSettledResult<T[]> | undefined, errors: unknown[]): T[] { if (result?.status === 'fulfilled') return result.value; if (result?.status === 'rejected') errors.push(result.reason); return [] }
function memberName(id: Id): string { return members.value.find(item => item.id === id)?.memberName ?? `成员 ${id}` }
function fieldName(key: string): string { return availableFields.value.find(item => item.stableFieldKey === key)?.businessName ?? (key || '—') }
function datasetVersionName(id: Id): string { const item = publishedDatasetOptions.value.find(option => option.version.id === id); return item ? `${item.dataset.name} V${item.version.versionNo}` : `版本 ${id}` }
function metricVersionName(id: Id): string { const metric = metrics.value.find(item => (metricVersions.value[item.id] ?? []).some(version => version.id === id)); const version = metric ? metricVersions.value[metric.id]?.find(item => item.id === id) : undefined; return metric ? `${metric.name} V${version?.versionNo ?? '?'}` : `版本 ${id}` }
function aggregationLabel(value: Aggregation): string { return ({ SUM: '求和 SUM', COUNT: '记录数 COUNT', COUNT_DISTINCT: '去重计数', AVG: '平均值 AVG', MIN: '最小值 MIN', MAX: '最大值 MAX' } as Record<Aggregation, string>)[value] }
function roleLabel(value: string): string { return value === 'MEASURE' ? '度量' : '维度' }
function statusLabel(value: string): string { return ({ DRAFT: '草稿', VALIDATED: '已校验', PUBLISHED: '已发布' } as Record<string, string>)[value] ?? value }
function statusTone(value: string): 'info' | 'warning' | 'success' { return value === 'PUBLISHED' ? 'success' : value === 'VALIDATED' ? 'warning' : 'info' }
function trialStatusLabel(value: MetricTrialResult['status']): string { return ({ DEFINED: '试算结果', UNDEFINED: '结果未定义', EMPTY: '没有可见样例', CROSS_CURRENCY: '跨币种禁止直接合计' })[value] }
function formatDate(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) }
function issue(code: string, path: string, message: string): ValidationIssue { return { code, path, message } }

void load()
</script>
