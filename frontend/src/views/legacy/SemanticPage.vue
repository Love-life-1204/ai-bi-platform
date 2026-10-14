<template>
  <div class="dataset-page">
    <section class="dataset-hero">
      <div><span class="eyebrow">SEMANTIC DATASETS</span><h2>数据集管理</h2><p>把物理字段转换为可治理、可发布并可供指标引用的业务语义版本。</p></div>
      <div class="dataset-stats">
        <span><strong>{{ datasets.length }}</strong><small>数据集</small></span>
        <span><strong>{{ publishedCount }}</strong><small>已发布</small></span>
        <span><strong>{{ draftCount }}</strong><small>待处理草稿</small></span>
      </div>
    </section>

    <section class="workbench-card dataset-list-card">
      <header class="dataset-card-heading">
        <div><span class="eyebrow">CURRENT WORKSPACE</span><h3>{{ workspaceName }}</h3><p>已发布版本才能被指标模块引用。</p></div>
        <div class="dataset-toolbar"><el-input v-model="keyword" clearable placeholder="搜索名称、编码或来源" /><el-button type="primary" :icon="Plus" @click="openCreate">新建数据集</el-button></div>
      </header>
      <UnifiedState v-if="loading" state="loading" description="正在加载数据集、版本和来源元数据。" />
      <UnifiedState v-else-if="loadError && !datasets.length" state="error" :description="loadError" retryable @retry="load" />
      <template v-else>
        <el-alert v-if="loadError" type="warning" :title="loadError" show-icon :closable="false" />
        <UnifiedState v-if="!filteredRows.length" state="empty" title="暂无数据集" description="从已采集元数据的来源表创建数据集草稿。" action-text="新建数据集" @action="openCreate" />
        <el-table v-else :data="filteredRows" stripe>
          <el-table-column label="数据集" min-width="190"><template #default="{ row }"><strong>{{ row.name }}</strong><small class="table-subtext">{{ row.code }}</small></template></el-table-column>
          <el-table-column prop="sourceName" label="来源" min-width="210" />
          <el-table-column label="版本" width="135"><template #default="{ row }"><strong>{{ row.versionText }}</strong><small v-if="row.hasDraft" class="table-subtext">含未发布草稿</small></template></el-table-column>
          <el-table-column label="发布状态" width="130"><template #default="{ row }"><el-tag :type="statusTone(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="更新时间" width="170"><template #default="{ row }">{{ formatDate(row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="340" align="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑草稿</el-button><el-button link type="primary" @click="validateDataset(row)">校验</el-button><el-button link @click="previewDataset(row)">权限预览</el-button><el-button link @click="showVersions(row)">版本</el-button><el-button link type="success" :disabled="row.status !== 'VALIDATED'" @click="publishDataset(row)">发布</el-button></template></el-table-column>
        </el-table>
      </template>
    </section>

    <CommonDialog v-model="wizardOpen" :title="editingDataset ? `编辑数据集 · ${editingDataset.name}` : '创建数据集'" width="1120px">
      <nav class="dataset-wizard-steps" aria-label="数据集创建步骤">
        <button v-for="(step, index) in wizardSteps" :key="step" type="button" :class="{ active: wizardStep === index, done: wizardStep > index }" @click="goToStep(index)"><span>{{ index + 1 }}</span>{{ step }}</button>
      </nav>

      <section v-if="wizardStep === 0" class="wizard-panel">
        <div class="wizard-intro"><span class="eyebrow">BASIC & SOURCE</span><h3>基础信息与来源表</h3><p>数据集编码创建后不可修改；来源必须是当前工作空间已采集的元数据对象。</p></div>
        <el-form label-position="top">
          <div class="dataset-basic-grid">
            <el-form-item label="数据集名称"><el-input v-model.trim="datasetForm.name" maxlength="200" placeholder="例如：销售订单明细" /></el-form-item>
            <el-form-item label="数据集编码"><el-input v-model.trim="datasetForm.code" :disabled="Boolean(editingDataset)" maxlength="64" placeholder="sales_orders" /></el-form-item>
            <el-form-item label="来源表" class="source-field"><el-select v-model="sourceSelection" filterable placeholder="选择已采集的 Schema / 表" @change="applySource"><el-option v-for="source in sourceCatalog" :key="sourceKey(source)" :label="`${source.datasourceName} · ${source.object.schema}.${source.object.name}`" :value="sourceKey(source)" /></el-select></el-form-item>
          </div>
        </el-form>
        <UnifiedState v-if="!sourceCatalog.length" state="empty" title="没有可用来源表" description="请先在数据源页面完成连接测试和元数据采集。" action-text="前往数据源" @action="router.push('/datasources')" />
        <div v-else-if="selectedSource" class="source-summary"><div><strong>{{ selectedSource.object.schema }}.{{ selectedSource.object.name }}</strong><small>{{ selectedSource.datasourceName }} · {{ selectedSource.object.type }}</small></div><el-tag effect="plain">{{ selectedSource.object.columns.length }} 个字段</el-tag></div>
      </section>

      <section v-else-if="wizardStep === 1" class="wizard-panel">
        <div class="wizard-intro"><span class="eyebrow">FIELD SEMANTICS</span><h3>字段与业务语义</h3><p>选择字段并配置业务名称、字段类型、维度/度量、敏感等级和展示格式。</p></div>
        <el-table :data="wizardFields" row-key="stableFieldKey" stripe max-height="470">
          <el-table-column label="使用" width="65" align="center"><template #default="{ row }"><input v-model="row.selected" type="checkbox" :aria-label="`使用字段 ${row.physicalName}`" /></template></el-table-column>
          <el-table-column label="物理字段" min-width="150"><template #default="{ row }"><strong>{{ row.physicalName }}</strong><small class="table-subtext">{{ row.stableFieldKey }}</small></template></el-table-column>
          <el-table-column label="业务名称" min-width="150"><template #default="{ row }"><el-input v-model.trim="row.businessName" :disabled="!row.selected" maxlength="200" /></template></el-table-column>
          <el-table-column label="字段类型" width="145"><template #default="{ row }"><el-select v-model="row.dataType" :disabled="!row.selected" @change="applyFormat(row)"><el-option v-for="type in fieldTypes" :key="type" :label="type" :value="type" /></el-select></template></el-table-column>
          <el-table-column label="角色" width="125"><template #default="{ row }"><el-select v-model="row.role" :disabled="!row.selected"><el-option label="维度" value="DIMENSION" /><el-option label="度量" value="MEASURE" /></el-select></template></el-table-column>
          <el-table-column label="敏感等级" width="145"><template #default="{ row }"><el-select v-model="row.sensitivity" :disabled="!row.selected"><el-option label="公开" value="PUBLIC" /><el-option label="内部" value="INTERNAL" /><el-option label="机密" value="CONFIDENTIAL" /><el-option label="受限" value="RESTRICTED" /></el-select></template></el-table-column>
          <el-table-column label="展示格式" min-width="170"><template #default="{ row }"><el-input v-model.trim="row.displayFormat" :disabled="!row.selected" placeholder="例如 #,##0.00" /></template></el-table-column>
        </el-table>
        <p class="selection-note">已选择 {{ selectedFields.length }} / {{ wizardFields.length }} 个字段</p>
      </section>

      <section v-else-if="wizardStep === 2" class="wizard-panel">
        <div class="wizard-intro relation-heading"><div><span class="eyebrow">RELATIONSHIPS</span><h3>数据集关联</h3><p>关联目标必须是同工作空间的已发布版本；基数配置会影响度量是否重复累计。</p></div><el-button :icon="Plus" @click="addRelation">添加关联</el-button></div>
        <UnifiedState v-if="!wizardRelations.length" state="empty" title="没有关联配置" description="单表数据集可直接进入下一步；需要关联维度时添加关联。" action-text="添加关联" @action="addRelation" />
        <div v-for="(relation, index) in wizardRelations" :key="`${index}-${relation.code}`" class="relation-editor" :class="{ invalid: relationIssues(index).length }">
          <header><strong>关联 {{ index + 1 }}</strong><el-button link type="danger" @click="wizardRelations.splice(index, 1)">移除</el-button></header>
          <div class="relation-grid">
            <el-form-item label="关联编码"><el-input v-model.trim="relation.code" maxlength="64" placeholder="customer" /></el-form-item>
            <el-form-item label="目标数据集版本"><el-select v-model="relation.relatedVersionId" filterable @change="resetRightField(relation)"><el-option v-for="option in relatedVersionOptions" :key="option.version.id" :label="`${option.dataset.name} · V${option.version.versionNo}`" :value="option.version.id" /></el-select></el-form-item>
            <el-form-item label="本数据集字段"><el-select v-model="relation.leftFieldKey"><el-option v-for="field in selectedFields" :key="field.stableFieldKey" :label="field.businessName" :value="field.stableFieldKey" /></el-select></el-form-item>
            <el-form-item label="目标字段"><el-select v-model="relation.rightFieldKey"><el-option v-for="field in relatedFields(relation.relatedVersionId)" :key="field.stableFieldKey" :label="`${field.businessName} · ${field.stableFieldKey}`" :value="field.stableFieldKey" /></el-select></el-form-item>
            <el-form-item label="关联基数"><el-select v-model="relation.cardinality"><el-option label="一对一" value="ONE_TO_ONE" /><el-option label="多对一" value="MANY_TO_ONE" /><el-option label="一对多" value="ONE_TO_MANY" /><el-option label="多对多（不可发布）" value="MANY_TO_MANY" /></el-select></el-form-item>
            <el-form-item label="去重规则"><el-select v-model="relation.deduplicationRule" clearable :disabled="relation.rightKeyUnique"><el-option label="保留第一条" value="FIRST" /><el-option label="保留最后一条" value="LAST" /><el-option label="最大更新时间" value="MAX_UPDATED_AT" /></el-select></el-form-item>
          </div>
          <div class="relation-controls"><label><el-switch v-model="relation.rightKeyUnique" />右侧字段保证唯一</label><label><el-switch v-model="relation.approved" />确认并批准关联</label></div>
          <el-alert v-if="relationIssues(index).length" type="warning" :title="relationIssues(index).map(issue => issue.message).join('；')" show-icon :closable="false" />
        </div>
      </section>

      <section v-else class="wizard-panel">
        <div class="wizard-intro"><span class="eyebrow">REVIEW DRAFT</span><h3>确认并保存草稿</h3><p>保存草稿不会发布；保存后需执行服务端校验，再由有发布权限的成员确认发布。</p></div>
        <div class="dataset-review-grid"><div><small>数据集</small><strong>{{ datasetForm.name }}</strong><span>{{ datasetForm.code }}</span></div><div><small>来源</small><strong>{{ selectedSource ? `${selectedSource.object.schema}.${selectedSource.object.name}` : '—' }}</strong><span>{{ selectedSource?.datasourceName ?? '—' }}</span></div><div><small>字段</small><strong>{{ selectedFields.length }}</strong><span>维度 {{ dimensionCount }} · 度量 {{ measureCount }}</span></div><div><small>关联</small><strong>{{ wizardRelations.length }}</strong><span>{{ wizardIssues.length ? `${wizardIssues.length} 个待修复问题` : '配置完整' }}</span></div></div>
        <el-alert v-if="wizardIssues.length" type="error" title="草稿存在本地校验问题，请返回相应步骤修复。" show-icon :closable="false"><ul><li v-for="issue in wizardIssues" :key="`${issue.path}-${issue.code}`">{{ issue.path }}：{{ issue.message }}</li></ul></el-alert>
        <el-alert v-else type="info" title="本地检查通过。保存后仍需执行服务端校验，服务端结果是发布依据。" show-icon :closable="false" />
      </section>

      <template #footer><div class="wizard-footer"><el-button @click="wizardOpen = false">取消</el-button><span /><el-button v-if="wizardStep > 0" @click="wizardStep--">上一步</el-button><el-button v-if="wizardStep < 3" type="primary" @click="nextStep">下一步</el-button><el-button v-else type="primary" :loading="saving" :disabled="Boolean(wizardIssues.length)" @click="saveDraft">保存草稿</el-button></div></template>
    </CommonDialog>

    <CommonDialog v-model="validationOpen" :title="`校验结果 · ${validationDataset?.name ?? ''}`" width="760px">
      <template v-if="validationResult"><el-alert :type="validationResult.valid ? 'success' : 'error'" :title="validationResult.valid ? '数据集校验通过，可以进入发布确认。' : `发现 ${validationResult.errors.length} 个阻断问题`" show-icon :closable="false" /><div v-if="validationResult.errors.length || validationResult.warnings.length" class="validation-issues"><button v-for="issue in [...validationResult.errors, ...validationResult.warnings]" :key="`${issue.path}-${issue.code}`" type="button" @click="locateIssue(issue)"><el-tag :type="validationResult.errors.includes(issue) ? 'danger' : 'warning'" size="small">{{ issue.code }}</el-tag><span><strong>{{ issue.path }}</strong><small>{{ issue.message }}</small></span><span>定位修改 →</span></button></div></template>
      <template #footer><el-button type="primary" @click="validationOpen = false">关闭</el-button></template>
    </CommonDialog>

    <CommonDialog v-model="previewOpen" :title="`权限预览 · ${previewDatasetName}`" width="980px">
      <UnifiedState v-if="previewLoading" state="loading" description="正在应用当前成员的行级与列级权限。" />
      <UnifiedState v-else-if="previewError" :state="previewForbidden ? 'forbidden' : 'error'" :description="previewError" />
      <template v-else-if="previewData"><div class="preview-policy-summary"><span>策略前 <strong>{{ previewData.totalBeforePolicy }}</strong> 行</span><span>当前成员可见 <strong>{{ previewData.visibleAfterPolicy }}</strong> 行</span><span>可见字段 <strong>{{ previewData.fields.length }}</strong> 个</span></div><UnifiedState v-if="!previewData.rows.length" state="empty" title="当前成员没有可见样例" description="行级权限可能过滤了全部数据，或当前版本尚无预览样例。" /><el-table v-else :data="previewData.rows" stripe max-height="460"><el-table-column v-for="field in previewData.fields" :key="field.id" :prop="field.stableFieldKey" :label="field.businessName" min-width="145"><template #default="{ row }">{{ formatPreviewValue(field, row[field.stableFieldKey]) }}</template></el-table-column></el-table></template>
      <template #footer><el-button type="primary" @click="previewOpen = false">关闭</el-button></template>
    </CommonDialog>

    <CommonDialog v-model="versionsOpen" :title="`历史版本 · ${versionsDataset?.name ?? ''}`" width="860px">
      <el-table :data="selectedVersions" stripe><el-table-column label="版本" width="90"><template #default="{ row }">V{{ row.versionNo }}</template></el-table-column><el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusTone(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column><el-table-column label="字段 / 关联" width="140"><template #default="{ row }">{{ row.fields.length }} / {{ row.relations.length }}</template></el-table-column><el-table-column prop="definitionHash" label="定义哈希" min-width="210" show-overflow-tooltip /><el-table-column label="发布时间" width="180"><template #default="{ row }">{{ row.publishedAt ? formatDate(row.publishedAt) : '未发布' }}</template></el-table-column></el-table>
      <template #footer><el-button type="primary" @click="versionsOpen = false">关闭</el-button></template>
    </CommonDialog>
  </div>
</template>

<script setup lang="ts">
import type { Id } from '../../api/resource-id'
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import CommonDialog from '../../components/common/CommonDialog.vue'
import UnifiedState from '../../components/common/UnifiedState.vue'
import { datasourceApi, semanticApi } from '../../api/runtime'
import { ApiClientError } from '../../api/client'
import type { Datasource, SourceObject } from '../../datasources/types'
import {
  DATASET_FIELD_TYPES,
  defaultDisplayFormat,
  issueSection,
  localDatasetIssues,
  normalizeDatasetType,
  type EditableDatasetField,
  type EditableDatasetRelation,
} from '../../semantic/dataset-validation'
import type { Dataset, DatasetField, DatasetVersion, PreviewData, ValidationIssue, ValidationResult } from '../../semantic/types'
import { useConfirm } from '../../composables/useConfirm'
import { useAppStore } from '../../stores/app'

interface SourceCatalogItem { datasourceId: Id; datasourceName: string; object: SourceObject }
interface DatasetRow extends Dataset { sourceName: string; versionText: string; hasDraft: boolean }

const app = useAppStore()
const router = useRouter()
const { confirm } = useConfirm()
const loading = ref(true)
const loadError = ref('')
const keyword = ref('')
const datasets = ref<Dataset[]>([])
const datasources = ref<Datasource[]>([])
const versionsByDataset = ref<Record<Id, DatasetVersion[]>>({})
const sourceCatalog = ref<SourceCatalogItem[]>([])

const wizardOpen = ref(false)
const wizardStep = ref(0)
const editingDataset = ref<Dataset>()
const saving = ref(false)
const sourceSelection = ref('')
const wizardFields = ref<EditableDatasetField[]>([])
const wizardRelations = ref<EditableDatasetRelation[]>([])
const datasetForm = reactive({ name: '', code: '', sourceObjectId: undefined as Id | undefined })

const validationOpen = ref(false)
const validationDataset = ref<Dataset>()
const validationResult = ref<ValidationResult>()
const previewOpen = ref(false)
const previewLoading = ref(false)
const previewError = ref('')
const previewForbidden = ref(false)
const previewDatasetName = ref('')
const previewData = ref<PreviewData>()
const versionsOpen = ref(false)
const versionsDataset = ref<Dataset>()

const wizardSteps = ['基础与来源', '字段语义', '关联配置', '确认草稿']
const fieldTypes = DATASET_FIELD_TYPES
const workspaceName = computed(() => app.workspaces.find(item => item.id === app.workspaceId)?.workspaceName ?? '当前工作空间')
const publishedCount = computed(() => datasets.value.filter(item => item.status === 'PUBLISHED').length)
const draftCount = computed(() => datasets.value.filter(item => item.status !== 'PUBLISHED' || currentDraft(item.id)).length)
const selectedSource = computed(() => sourceCatalog.value.find(item => sourceKey(item) === sourceSelection.value))
const selectedFields = computed(() => wizardFields.value.filter(field => field.selected))
const dimensionCount = computed(() => selectedFields.value.filter(field => field.role === 'DIMENSION').length)
const measureCount = computed(() => selectedFields.value.filter(field => field.role === 'MEASURE').length)
const wizardIssues = computed(() => localDatasetIssues(wizardFields.value, wizardRelations.value))
const relatedVersionOptions = computed(() => datasets.value.flatMap(dataset => (versionsByDataset.value[dataset.id] ?? [])
  .filter(version => version.status === 'PUBLISHED' && dataset.id !== editingDataset.value?.id)
  .map(version => ({ dataset, version }))))
const selectedVersions = computed(() => versionsDataset.value ? versionsByDataset.value[versionsDataset.value.id] ?? [] : [])
const datasetRows = computed<DatasetRow[]>(() => datasets.value.map(dataset => {
  const versions = versionsByDataset.value[dataset.id] ?? []
  const draft = versions.find(version => version.status === 'DRAFT' || version.status === 'VALIDATED')
  const published = versions.find(version => version.id === dataset.currentVersionId)
  const active = draft ?? published ?? versions.at(-1)
  return { ...dataset, sourceName: sourceName(active?.sourceObjectId), versionText: active ? `V${active.versionNo}` : '—', hasDraft: Boolean(draft) }
}))
const filteredRows = computed(() => datasetRows.value.filter(row => !keyword.value
  || `${row.name} ${row.code} ${row.sourceName}`.toLowerCase().includes(keyword.value.toLowerCase())))

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  const [datasetResult, sourceResult] = await Promise.allSettled([semanticApi.datasets(app.workspaceId), datasourceApi.list(app.workspaceId)])
  if (datasetResult.status === 'fulfilled') datasets.value = datasetResult.value
  if (sourceResult.status === 'fulfilled') datasources.value = sourceResult.value
  const errors: unknown[] = []
  if (datasetResult.status === 'rejected') errors.push(datasetResult.reason)
  if (sourceResult.status === 'rejected') errors.push(sourceResult.reason)
  const versionResults = await Promise.allSettled(datasets.value.map(dataset => semanticApi.datasetVersions(dataset.id)))
  versionsByDataset.value = Object.fromEntries(datasets.value.map((dataset, index) => {
    const result = versionResults[index]
    if (result?.status === 'rejected') errors.push(result.reason)
    return [dataset.id, result?.status === 'fulfilled' ? result.value : []]
  }))
  const snapshotResults = await Promise.allSettled(datasources.value.filter(item => item.status === 'ACTIVE').map(async source => ({ source, snapshots: await datasourceApi.metadataSnapshots(source.id) })))
  sourceCatalog.value = snapshotResults.flatMap(result => {
    if (result.status === 'rejected') { errors.push(result.reason); return [] }
    return (result.value.snapshots.at(-1)?.objects ?? []).filter(object => object.id).map(object => ({ datasourceId: result.value.source.id, datasourceName: result.value.source.name, object }))
  })
  if (errors.length) loadError.value = errors[0] instanceof Error ? errors[0].message : '部分数据集信息加载失败'
  loading.value = false
}

function openCreate(): void {
  editingDataset.value = undefined
  wizardStep.value = 0
  Object.assign(datasetForm, { name: '', code: '', sourceObjectId: undefined })
  sourceSelection.value = ''
  wizardFields.value = []
  wizardRelations.value = []
  wizardOpen.value = true
}

async function openEdit(row: DatasetRow): Promise<void> {
  const dataset = datasets.value.find(item => item.id === row.id)
  if (!dataset) return
  editingDataset.value = dataset
  wizardStep.value = 0
  Object.assign(datasetForm, { name: dataset.name, code: dataset.code, sourceObjectId: undefined })
  const detail = await semanticApi.dataset(dataset.id)
  const versions = versionsByDataset.value[dataset.id] ?? []
  const current = detail.draft ?? versions.find(item => item.id === dataset.currentVersionId) ?? versions.at(-1)
  if (!current) { ElMessage.warning('数据集没有可编辑版本'); return }
  datasetForm.sourceObjectId = current.sourceObjectId
  const source = sourceCatalog.value.find(item => item.object.id === current.sourceObjectId)
  sourceSelection.value = source ? sourceKey(source) : ''
  wizardFields.value = mergeFields(source?.object, current.fields)
  wizardRelations.value = current.relations.map(relation => ({ code: relation.code, relatedVersionId: relation.relatedVersionId, leftFieldKey: relation.leftFieldKey, rightFieldKey: relation.rightFieldKey, cardinality: relation.cardinality, approved: relation.approved, rightKeyUnique: relation.rightKeyUnique, ...(relation.deduplicationRule ? { deduplicationRule: relation.deduplicationRule } : {}) }))
  wizardOpen.value = true
}

function applySource(): void {
  const source = selectedSource.value
  datasetForm.sourceObjectId = source?.object.id
  wizardFields.value = mergeFields(source?.object, [])
  wizardRelations.value = []
}

function mergeFields(object: SourceObject | undefined, existing: DatasetField[]): EditableDatasetField[] {
  if (!object) return existing.map(field => ({ selected: true, stableFieldKey: field.stableFieldKey, ...(field.sourceColumnId ? { sourceColumnId: field.sourceColumnId } : {}), physicalName: field.physicalName, businessName: field.businessName, dataType: normalizeDatasetType(field.dataType), role: field.role, sensitivity: field.sensitivity, displayFormat: field.displayFormat ?? defaultDisplayFormat(normalizeDatasetType(field.dataType)) }))
  const byKey = new Map(existing.map(field => [field.stableFieldKey, field]))
  return object.columns.map(column => {
    const field = byKey.get(column.stableKey)
    const type = normalizeDatasetType(field?.dataType ?? column.dataType)
    return { selected: Boolean(field) || existing.length === 0, stableFieldKey: column.stableKey, ...(column.id ? { sourceColumnId: column.id } : {}), physicalName: column.name, businessName: field?.businessName ?? column.comment ?? column.name, dataType: type, role: field?.role ?? (type === 'DECIMAL' || type === 'INTEGER' || type === 'LONG' ? 'MEASURE' : 'DIMENSION'), sensitivity: field?.sensitivity ?? 'INTERNAL', displayFormat: field?.displayFormat ?? defaultDisplayFormat(type) }
  })
}

function nextStep(): void {
  if (wizardStep.value === 0 && (!datasetForm.name || !/^[a-z][a-z0-9_]{1,63}$/.test(datasetForm.code) || !datasetForm.sourceObjectId)) { ElMessage.warning('请填写名称、合法编码并选择来源表'); return }
  if (wizardStep.value === 1) {
    const issues = localDatasetIssues(wizardFields.value, [])
    if (!selectedFields.value.length || issues.length) { ElMessage.warning(issues[0]?.message ?? '请至少选择一个字段'); return }
  }
  if (wizardStep.value === 2 && wizardIssues.value.length) { ElMessage.warning('请先修复关联配置问题'); return }
  wizardStep.value = Math.min(3, wizardStep.value + 1)
}

function goToStep(index: number): void { if (index <= wizardStep.value) wizardStep.value = index }
function applyFormat(field: EditableDatasetField): void { if (!field.displayFormat) field.displayFormat = defaultDisplayFormat(field.dataType) }

function addRelation(): void {
  const target = relatedVersionOptions.value[0]?.version
  wizardRelations.value.push({ code: `relation_${wizardRelations.value.length + 1}`, relatedVersionId: target?.id ?? '', leftFieldKey: selectedFields.value[0]?.stableFieldKey ?? '', rightFieldKey: target?.fields[0]?.stableFieldKey ?? '', cardinality: 'MANY_TO_ONE', approved: false, rightKeyUnique: false })
}

function resetRightField(relation: EditableDatasetRelation): void { relation.rightFieldKey = relatedFields(relation.relatedVersionId)[0]?.stableFieldKey ?? '' }
function relatedFields(versionId: Id): DatasetField[] { return relatedVersionOptions.value.find(option => option.version.id === versionId)?.version.fields ?? [] }
function relationIssues(index: number): ValidationIssue[] { return wizardIssues.value.filter(issue => issue.path.startsWith(`relations[${index}]`)) }

async function saveDraft(): Promise<void> {
  if (!datasetForm.sourceObjectId || wizardIssues.value.length) return
  saving.value = true
  try {
    const fields = selectedFields.value.map(field => ({ stableFieldKey: field.stableFieldKey, ...(field.sourceColumnId ? { sourceColumnId: field.sourceColumnId } : {}), physicalName: field.physicalName, businessName: field.businessName, dataType: field.dataType, role: field.role, sensitivity: field.sensitivity, ...(field.displayFormat ? { displayFormat: field.displayFormat } : {}) }))
    const relations = wizardRelations.value.map(relation => ({ ...relation, ...(relation.deduplicationRule ? { deduplicationRule: relation.deduplicationRule } : {}) }))
    if (editingDataset.value) await semanticApi.updateDataset(editingDataset.value.id, { name: datasetForm.name, sourceObjectId: datasetForm.sourceObjectId, fields, relations, previewRows: [], lockVersion: editingDataset.value.lockVersion })
    else await semanticApi.createDataset({ workspaceId: app.workspaceId, code: datasetForm.code, name: datasetForm.name, sourceObjectId: datasetForm.sourceObjectId, fields, relations, previewRows: [] })
    wizardOpen.value = false
    await load()
    ElMessage.success('数据集草稿已保存，请继续执行服务端校验')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '数据集草稿保存失败') }
  finally { saving.value = false }
}

async function validateDataset(row: DatasetRow): Promise<void> {
  const dataset = datasets.value.find(item => item.id === row.id)
  if (!dataset) return
  validationDataset.value = dataset
  try { validationResult.value = await semanticApi.validateDataset(dataset.id); validationOpen.value = true; await load() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '数据集校验失败') }
}

async function locateIssue(issue: ValidationIssue): Promise<void> {
  const dataset = validationDataset.value
  if (!dataset) return
  validationOpen.value = false
  const row = datasetRows.value.find(item => item.id === dataset.id)
  if (!row) return
  await openEdit(row)
  wizardStep.value = issueSection(issue.path)
}

async function publishDataset(row: DatasetRow): Promise<void> {
  const dataset = datasets.value.find(item => item.id === row.id)
  if (!dataset || !await confirm(`发布“${dataset.name}”后，该版本将不可变并可被指标引用。`, { title: '发布数据集版本', confirmText: '确认发布' })) return
  try { const version = await semanticApi.publishDataset(dataset.id); ElMessage.success(`数据集 V${version.versionNo} 已发布`); await load() }
  catch (error) { await load(); ElMessage.error(error instanceof Error ? `${error.message}，列表已刷新` : '发布冲突，列表已刷新') }
}

async function previewDataset(row: DatasetRow): Promise<void> {
  previewOpen.value = true
  previewLoading.value = true
  previewError.value = ''
  previewForbidden.value = false
  previewData.value = undefined
  previewDatasetName.value = row.name
  try { previewData.value = await semanticApi.previewDataset(row.id) }
  catch (error) { previewForbidden.value = error instanceof ApiClientError && error.status === 403; previewError.value = error instanceof Error ? error.message : '权限预览加载失败' }
  finally { previewLoading.value = false }
}

async function showVersions(row: DatasetRow): Promise<void> {
  const dataset = datasets.value.find(item => item.id === row.id)
  if (!dataset) return
  versionsByDataset.value[dataset.id] = await semanticApi.datasetVersions(dataset.id)
  versionsDataset.value = dataset
  versionsOpen.value = true
}

function currentDraft(datasetId: Id): DatasetVersion | undefined { return (versionsByDataset.value[datasetId] ?? []).find(version => version.status === 'DRAFT' || version.status === 'VALIDATED') }
function sourceKey(source: SourceCatalogItem): string { return `${source.datasourceId}:${source.object.id}` }
function sourceName(sourceObjectId?: Id): string { const source = sourceCatalog.value.find(item => item.object.id === sourceObjectId); return source ? `${source.datasourceName} · ${source.object.schema}.${source.object.name}` : sourceObjectId ? `来源对象 ${sourceObjectId}` : '未绑定来源' }
function statusLabel(status: string): string { return ({ DRAFT: '草稿', VALIDATED: '已校验', PUBLISHED: '已发布' } as Record<string, string>)[status] ?? status }
function statusTone(status: string): 'info' | 'warning' | 'success' { return status === 'PUBLISHED' ? 'success' : status === 'VALIDATED' ? 'warning' : 'info' }
function formatDate(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) }
function formatPreviewValue(field: DatasetField, value: unknown): string { if (value === null || value === undefined) return '—'; if (typeof value === 'number' && field.displayFormat?.includes(',')) return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: field.displayFormat.includes('.00') ? 2 : 0 }).format(value); return String(value) }

void load()
</script>
