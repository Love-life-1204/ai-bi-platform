<template>
  <div class="datasource-page">
    <section class="datasource-summary">
      <div><span class="eyebrow">DATA CONNECTIONS</span><h2>数据源连接</h2><p>仅允许只读数据库账号；保存后的凭据不会返回浏览器。</p></div>
      <div class="datasource-stats">
        <span><strong>{{ datasources.length }}</strong><small>数据源</small></span>
        <span><strong>{{ activeCount }}</strong><small>已启用</small></span>
        <span><strong>{{ successCount }}</strong><small>连接正常</small></span>
      </div>
    </section>

    <section class="workbench-card datasource-list-card">
      <header>
        <div><span class="eyebrow">CURRENT WORKSPACE</span><h3>{{ workspaceName }}</h3></div>
        <div class="datasource-toolbar"><el-input v-model="keyword" clearable placeholder="搜索名称、编码或地址" /><el-button type="primary" :icon="Plus" @click="openCreate">新增数据源</el-button></div>
      </header>
      <UnifiedState v-if="loading" state="loading" description="正在加载当前工作空间的数据源。" />
      <UnifiedState v-else-if="errorMessage && !datasources.length" state="error" :description="errorMessage" retryable @retry="load" />
      <template v-else>
        <el-alert v-if="errorMessage" class="datasource-alert" type="warning" :title="errorMessage" show-icon :closable="false" />
        <CommonTable v-if="filteredRows.length" :rows="filteredRows" :columns="columns" :actions-width="300">
          <template #cell-name="{ row }">
            <div class="datasource-name"><span :class="`source-${String(row.type).toLowerCase()}`"><el-icon><Coin v-if="row.type === 'MYSQL'" /><DataLine v-else /></el-icon></span><div><strong>{{ row.name }}</strong><small>{{ row.code }}</small></div></div>
          </template>
          <template #cell-type="{ value }"><el-tag effect="plain">{{ sourceTypeText(String(value)) }}</el-tag></template>
          <template #cell-connection="{ row }"><div class="connection-cell"><span class="connection-dot" :class="connectionTone(row)" /><div><strong>{{ connectionText(row) }}</strong><small>{{ row.lastTestText }}</small></div></div></template>
          <template #cell-status="{ value }"><el-tag :type="value === 'ACTIVE' ? 'success' : 'info'" effect="light" size="small">{{ value === 'ACTIVE' ? '已启用' : '已停用' }}</el-tag></template>
          <template #actions="{ row }">
            <el-button link type="primary" :disabled="row.status !== 'ACTIVE'" @click="testSaved(row)">测试</el-button>
            <el-button link type="primary" :disabled="row.status !== 'ACTIVE'" @click="openMetadata(row)">元数据</el-button>
            <el-button link :disabled="row.status !== 'ACTIVE'" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="deactivate(row)">停用</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </CommonTable>
        <UnifiedState v-else state="empty" title="暂无数据源" description="连接 MySQL、PostgreSQL，或上传 CSV/XLSX 文件开始分析。" action-text="新增数据源" @action="openCreate" />
      </template>
    </section>

    <CommonDialog v-model="sourceTypeOpen" title="选择数据源类型" width="720px" confirm-text="继续" @confirm="continueCreate">
      <div class="source-type-grid">
        <button v-for="option in sourceOptions" :key="option.value" type="button" :class="{ active: selectedSourceType === option.value }" @click="selectedSourceType = option.value">
          <span><el-icon :size="24"><component :is="option.icon" /></el-icon></span><strong>{{ option.label }}</strong><small>{{ option.description }}</small>
        </button>
      </div>
    </CommonDialog>

    <CommonDialog v-model="formOpen" :title="editing ? '编辑数据源' : '新增数据库连接'" width="760px" :confirm-loading="saving" confirm-text="保存数据源" @confirm="save">
      <el-alert v-if="editing" title="安全提示：已保存的用户名和密码不会回显。凭据留空表示继续使用原凭据。" type="info" show-icon :closable="false" />
      <el-form label-position="top" class="datasource-form">
        <div class="datasource-form-grid">
          <el-form-item label="显示名称"><el-input v-model.trim="form.name" maxlength="200" placeholder="例如：销售订单库" /></el-form-item>
          <el-form-item label="数据源编码"><el-input v-model.trim="form.code" :disabled="Boolean(editing)" maxlength="64" placeholder="例如：sales_orders" /></el-form-item>
          <el-form-item label="数据库类型"><el-select v-model="form.type" :disabled="Boolean(editing)" @change="applyDefaultPort"><el-option label="MySQL" value="MYSQL" /><el-option label="PostgreSQL" value="POSTGRESQL" /></el-select></el-form-item>
          <el-form-item label="主机地址"><el-input v-model.trim="form.host" placeholder="db.example.com" /></el-form-item>
          <el-form-item label="端口"><el-input v-model.number="form.port" type="number" min="1" max="65535" /></el-form-item>
          <el-form-item label="数据库名"><el-input v-model.trim="form.database" placeholder="analytics" /></el-form-item>
          <el-form-item label="用户名"><el-input v-model="form.username" autocomplete="off" :placeholder="editing ? '留空则不更新' : '只读账号'" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" :placeholder="editing ? '留空则不更新' : '请输入密码'" /></el-form-item>
        </div>
        <label class="tls-control"><input v-model="form.tls" type="checkbox" /><span><strong>启用 TLS 加密</strong><small>生产环境建议始终启用；内网及保留地址仍受服务端安全策略限制。</small></span></label>
      </el-form>
      <div v-if="formTest.kind !== 'idle'" class="connection-result-card" :class="`result-${formTest.kind}`">
        <span class="connection-dot" :class="testTone(formTest.kind)" /><div><strong>{{ formTest.kind === 'testing' ? '正在测试连接' : formTest.message }}</strong><small v-if="formTest.kind === 'success'">响应耗时 {{ formTest.latencyMs }} ms</small></div>
      </div>
      <template #footer><el-button @click="formOpen = false">取消</el-button><el-button :loading="testingDraft" @click="testDraft">测试连接</el-button><el-button type="primary" :loading="saving" @click="save">保存数据源</el-button></template>
    </CommonDialog>

    <CommonDialog v-model="metadataOpen" :title="`元数据 · ${metadataSource?.name ?? ''}`" width="920px" confirm-text="使用选中源表" :confirm-loading="collectingMetadata" @confirm="useSelectedTables">
      <div class="metadata-toolbar"><div><strong>{{ latestSnapshot ? `版本 ${latestSnapshot.version}` : '尚未采集' }}</strong><small>{{ latestSnapshot ? `采集于 ${formatDate(latestSnapshot.collectedAt)}` : '先采集 Schema、表和字段信息' }}</small></div><el-button type="primary" plain :loading="collectingMetadata" @click="collectMetadata">{{ latestSnapshot ? '重新采集' : '采集元数据' }}</el-button></div>
      <UnifiedState v-if="metadataLoading" state="loading" description="正在读取元数据快照。" />
      <UnifiedState v-else-if="!latestSnapshot" state="empty" title="暂无元数据" description="连接验证通过后，点击采集元数据读取 Schema、表及字段。" />
      <div v-else class="metadata-browser">
        <aside><div v-for="schema in schemaGroups" :key="schema.name" class="schema-group"><strong>{{ schema.name }}</strong><button v-for="object in schema.objects" :key="objectKey(object)" type="button" :class="{ active: selectedObjectKey === objectKey(object) }" @click="selectedObjectKey = objectKey(object)"><input type="checkbox" :checked="selectedTables.includes(objectKey(object))" @click.stop="toggleTable(object)" /><span>{{ object.name }}</span><small>{{ object.type }}</small></button></div></aside>
        <section>
          <header v-if="selectedObject"><div><h4>{{ selectedObject.schema }}.{{ selectedObject.name }}</h4><p>{{ selectedObject.comment || '暂无表说明' }}</p></div><el-tag effect="plain">{{ selectedObject.columns.length }} 个字段</el-tag></header>
          <CommonTable v-if="selectedObject" :rows="columnRows" :columns="columnColumns">
            <template #cell-name="{ row }"><strong>{{ row.name }}</strong><el-tag v-if="row.primaryKey" type="warning" size="small" effect="plain" class="pk-tag">PK</el-tag></template>
            <template #cell-nullable="{ value }">{{ value ? '允许为空' : '必填' }}</template>
          </CommonTable>
          <UnifiedState v-else state="empty" title="请选择源表" description="从左侧选择表后查看字段元数据。" />
        </section>
      </div>
      <p v-if="selectedTables.length" class="metadata-selection">已选择 {{ selectedTables.length }} 张源表：{{ selectedTables.join('，') }}</p>
    </CommonDialog>

  </div>
</template>

<script setup lang="ts">
import type { Id } from '../../api/resource-id'
import { resourceId } from '../../api/resource-id'
import { computed, markRaw, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Coin, DataLine, Document, Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import CommonDialog from '../../components/common/CommonDialog.vue'
import CommonTable, { type TableColumn } from '../../components/common/CommonTable.vue'
import UnifiedState from '../../components/common/UnifiedState.vue'
import { datasourceApi, organizationApi } from '../../api/runtime'
import { connectionFailure, connectionSuccess } from '../../datasources/connection-status'
import type { ConnectionTestState, Datasource, DatasourceInput, DatasourceType, MetadataSnapshot, SourceObject } from '../../datasources/types'
import { useConfirm } from '../../composables/useConfirm'
import { useAppStore } from '../../stores/app'

type SourceChoice = DatasourceType | 'FILE'
const app = useAppStore()
const router = useRouter()
const { confirm } = useConfirm()
const loading = ref(true)
const errorMessage = ref('')
const keyword = ref('')
const datasources = ref<Datasource[]>([])
const memberNames = ref<Record<Id, string>>({})
const testStates = ref<Record<Id, ConnectionTestState>>({})
const sourceTypeOpen = ref(false)
const selectedSourceType = ref<SourceChoice>('MYSQL')
const formOpen = ref(false)
const saving = ref(false)
const testingDraft = ref(false)
const editing = ref<Datasource>()
const formTest = ref<ConnectionTestState>({ kind: 'idle', message: '保存前请测试连接' })
const metadataOpen = ref(false)
const metadataLoading = ref(false)
const collectingMetadata = ref(false)
const metadataSource = ref<Datasource>()
const snapshots = ref<MetadataSnapshot[]>([])
const selectedObjectKey = ref('')
const selectedTables = ref<string[]>([])

const form = reactive({ name: '', code: '', type: 'MYSQL' as DatasourceType, host: '', port: 3306, database: '', username: '', password: '', tls: true })
const sourceOptions = [
  { value: 'MYSQL' as const, label: 'MySQL', description: '连接 MySQL 5.7/8.0 只读实例', icon: markRaw(Coin) },
  { value: 'POSTGRESQL' as const, label: 'PostgreSQL', description: '连接 PostgreSQL 12+ 只读实例', icon: markRaw(DataLine) },
  { value: 'FILE' as const, label: '文件数据源', description: '上传 CSV 或 XLSX 并预览字段', icon: markRaw(Document) },
]
const columns: TableColumn[] = [
  { key: 'name', label: '数据源', minWidth: 190 }, { key: 'type', label: '类型', width: 120 },
  { key: 'endpointText', label: '连接地址', minWidth: 220 }, { key: 'connection', label: '连接状态', minWidth: 170 },
  { key: 'ownerName', label: '负责人', width: 130 }, { key: 'status', label: '状态', width: 100 },
]
const columnColumns: TableColumn[] = [
  { key: 'name', label: '字段', minWidth: 170 }, { key: 'dataType', label: '数据类型', width: 150 },
  { key: 'nullable', label: '可空', width: 100 }, { key: 'comment', label: '说明', minWidth: 180 },
]

const workspaceName = computed(() => app.workspaces.find(item => item.id === app.workspaceId)?.workspaceName ?? '当前工作空间')
const activeCount = computed(() => datasources.value.filter(item => item.status === 'ACTIVE').length)
const successCount = computed(() => datasources.value.filter(item => item.lastTestStatus === 'SUCCESS').length)
const filteredRows = computed<Record<string, unknown>[]>(() => datasources.value
  .filter(item => !keyword.value || `${item.name} ${item.code} ${item.endpoint.host}`.toLowerCase().includes(keyword.value.toLowerCase()))
  .map(item => ({ ...item, endpointText: `${item.endpoint.host}:${item.endpoint.port}/${item.endpoint.database}`, ownerName: item.ownerMemberId ? memberNames.value[item.ownerMemberId] ?? `成员 ${item.ownerMemberId}` : '未记录', lastTestText: item.lastTestAt ? formatDate(item.lastTestAt) : '尚未测试' })))
const latestSnapshot = computed(() => snapshots.value.at(-1))
const schemaGroups = computed(() => {
  const values = new Map<string, SourceObject[]>()
  for (const object of latestSnapshot.value?.objects ?? []) values.set(object.schema, [...(values.get(object.schema) ?? []), object])
  return [...values.entries()].map(([name, objects]) => ({ name, objects }))
})
const selectedObject = computed(() => latestSnapshot.value?.objects.find(item => objectKey(item) === selectedObjectKey.value))
const columnRows = computed<Record<string, unknown>[]>(() => (selectedObject.value?.columns ?? []).map(item => ({ ...item, comment: item.comment || '—' })))

async function load(): Promise<void> {
  loading.value = true; errorMessage.value = ''
  const [sourceResult, memberResult] = await Promise.allSettled([datasourceApi.list(app.workspaceId), organizationApi.members(resourceId(app.tenantId))])
  if (sourceResult.status === 'fulfilled') datasources.value = sourceResult.value
  else errorMessage.value = sourceResult.reason instanceof Error ? sourceResult.reason.message : '数据源加载失败'
  if (memberResult.status === 'fulfilled') memberNames.value = Object.fromEntries(memberResult.value.map(item => [item.id, item.memberName]))
  loading.value = false
}
function openCreate(): void { selectedSourceType.value = 'MYSQL'; sourceTypeOpen.value = true }
function continueCreate(): void {
  sourceTypeOpen.value = false
  if (selectedSourceType.value === 'FILE') { void router.push('/sync'); return }
  editing.value = undefined
  Object.assign(form, { name: '', code: '', type: selectedSourceType.value, host: '', port: selectedSourceType.value === 'MYSQL' ? 3306 : 5432, database: '', username: '', password: '', tls: true })
  formTest.value = { kind: 'idle', message: '保存前请测试连接' }; formOpen.value = true
}
function openEdit(row: Record<string, unknown>): void {
  const source = datasources.value.find(item => item.id === resourceId(row.id)); if (!source) return
  editing.value = source
  Object.assign(form, { name: source.name, code: source.code, type: source.type, host: source.endpoint.host, port: source.endpoint.port, database: source.endpoint.database, username: '', password: '', tls: source.endpoint.tls })
  formTest.value = { kind: 'idle', message: '如未修改地址，可使用已保存凭据测试' }; formOpen.value = true
}
function applyDefaultPort(): void { form.port = form.type === 'MYSQL' ? 3306 : 5432 }
function validateForm(requireCredential = !editing.value): string | undefined {
  if (!form.name || !/^[a-z][a-z0-9_]*$/.test(form.code) || !form.host || !form.database || form.port < 1 || form.port > 65535) return '请完整填写名称、合法编码、地址、端口和数据库名'
  if ((form.username && !form.password) || (!form.username && form.password)) return '更新凭据时必须同时填写用户名和密码'
  if (requireCredential && (!form.username || !form.password)) return '请输入只读数据库账号和密码'
  return undefined
}
function formInput(): DatasourceInput { return { workspaceId: app.workspaceId, code: form.code, name: form.name, type: form.type, endpoint: { host: form.host, port: Number(form.port), database: form.database, tls: form.tls }, credential: { username: form.username, password: form.password } } }
async function testDraft(): Promise<void> {
  const endpointChanged = editing.value && (editing.value.endpoint.host !== form.host || editing.value.endpoint.port !== Number(form.port) || editing.value.endpoint.database !== form.database || editing.value.endpoint.tls !== form.tls)
  const usesSaved = Boolean(editing.value && !form.username && !form.password && !endpointChanged)
  const validation = validateForm(!usesSaved)
  if (validation) { ElMessage.warning(validation); return }
  testingDraft.value = true; formTest.value = { kind: 'testing', message: '正在验证网络、认证与只读权限…' }
  try { formTest.value = connectionSuccess(usesSaved ? await datasourceApi.testSaved(editing.value!.id) : await datasourceApi.testDraft(formInput())) }
  catch (error) { formTest.value = connectionFailure(error) }
  finally { testingDraft.value = false }
}
async function save(): Promise<void> {
  const validation = validateForm(!editing.value)
  if (validation) { ElMessage.warning(validation); return }
  saving.value = true
  try {
    const input = formInput()
    if (editing.value) await datasourceApi.update(editing.value.id, { name: input.name, endpoint: input.endpoint, ...(form.username && form.password ? { credential: input.credential } : {}), lockVersion: editing.value.lockVersion })
    else await datasourceApi.create(input)
    form.password = ''; form.username = ''; formOpen.value = false; await load(); ElMessage.success(editing.value ? '数据源已更新' : '数据源已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}
async function testSaved(row: Record<string, unknown>): Promise<void> {
  const source = datasources.value.find(item => item.id === resourceId(row.id)); if (!source) return
  testStates.value[source.id] = { kind: 'testing', message: '正在测试…' }
  try { testStates.value[source.id] = connectionSuccess(await datasourceApi.testSaved(source.id)); await load() }
  catch (error) { testStates.value[source.id] = connectionFailure(error); await load() }
}
async function deactivate(row: Record<string, unknown>): Promise<void> {
  const source = datasources.value.find(item => item.id === resourceId(row.id)); if (!source) return
  const dependencies = await datasourceApi.dependencies(source.id)
  const suffix = dependencies.syncJobs.length ? `\n受影响的同步任务：${dependencies.syncJobs.map(item => item.jobCode).join('、')}` : ''
  if (!await confirm(`停用“${source.name}”后，其保存凭据将立即失效。${suffix}`, { title: '停用数据源', confirmText: '确认停用', danger: true })) return
  await datasourceApi.deactivate(source); await load(); ElMessage.success('数据源已停用')
}
async function remove(row: Record<string, unknown>): Promise<void> {
  let source = datasources.value.find(item => item.id === resourceId(row.id)); if (!source) return
  const dependencies = await datasourceApi.dependencies(source.id)
  if (dependencies.syncJobs.length) { await ElMessageBox.alert(`请先处理以下同步任务：${dependencies.syncJobs.map(item => item.jobCode).join('、')}`, '无法删除：存在依赖', { type: 'warning', confirmButtonText: '知道了' }); return }
  const message = source.status === 'ACTIVE' ? `“${source.name}”将先停用并删除，保存凭据会被销毁。` : `确定永久删除“${source.name}”吗？`
  if (!await confirm(message, { title: '删除数据源', confirmText: '删除', danger: true })) return
  if (source.status === 'ACTIVE') source = await datasourceApi.deactivate(source)
  await datasourceApi.delete(source); await load(); ElMessage.success('数据源已删除')
}
async function openMetadata(row: Record<string, unknown>): Promise<void> {
  metadataSource.value = datasources.value.find(item => item.id === resourceId(row.id)); if (!metadataSource.value) return
  metadataOpen.value = true; metadataLoading.value = true; selectedTables.value = []
  try { snapshots.value = await datasourceApi.metadataSnapshots(metadataSource.value.id); selectFirstObject() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '元数据加载失败') }
  finally { metadataLoading.value = false }
}
async function collectMetadata(): Promise<void> {
  if (!metadataSource.value) return
  collectingMetadata.value = true
  try { await datasourceApi.collectMetadata(metadataSource.value.id); snapshots.value = await datasourceApi.metadataSnapshots(metadataSource.value.id); selectFirstObject(); ElMessage.success('元数据采集完成') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '元数据采集失败') }
  finally { collectingMetadata.value = false }
}
function selectFirstObject(): void { selectedObjectKey.value = latestSnapshot.value?.objects[0] ? objectKey(latestSnapshot.value.objects[0]) : '' }
function objectKey(object: SourceObject): string { return `${object.schema}.${object.name}` }
function toggleTable(object: SourceObject): void { const key = objectKey(object); selectedTables.value = selectedTables.value.includes(key) ? selectedTables.value.filter(value => value !== key) : [...selectedTables.value, key] }
function useSelectedTables(): void {
  if (!selectedTables.value.length || !metadataSource.value) { ElMessage.warning('请至少选择一张源表'); return }
  localStorage.setItem(`aibi.datasourceSelection.${app.tenantId}.${app.workspaceId}`, JSON.stringify({ datasourceId: metadataSource.value.id, tables: selectedTables.value }))
  metadataOpen.value = false; void router.push('/sync')
}
function sourceTypeText(value: string): string { return value === 'MYSQL' ? 'MySQL' : value === 'POSTGRESQL' ? 'PostgreSQL' : value }
function connectionTone(row: Record<string, unknown>): string { const state = testStates.value[resourceId(row.id)]; return state ? testTone(state.kind) : row.lastTestStatus === 'SUCCESS' ? 'success' : row.lastTestStatus ? 'danger' : 'muted' }
function connectionText(row: Record<string, unknown>): string { const state = testStates.value[resourceId(row.id)]; if (state) return state.message; if (row.lastTestStatus === 'SUCCESS') return '连接正常'; if (row.lastTestStatus) return '最近测试失败'; return '尚未测试' }
function testTone(kind: ConnectionTestState['kind']): string { return kind === 'success' ? 'success' : kind === 'testing' ? 'warning' : kind === 'idle' ? 'muted' : 'danger' }
function formatDate(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) }

load()
</script>
