<template>
  <div class="ai-analysis-page">
    <section class="ai-analysis-hero">
      <div><span class="eyebrow">GROUNDED AI ANALYTICS</span><h2>AI 智能数据分析</h2><p>在当前成员权限范围内检索已发布语义资产，所有事实均来自可追踪的受控查询。</p></div>
      <div class="ai-analysis-stats"><span><strong>{{ workspaceName }}</strong><small>当前工作空间</small></span><span><strong>{{ publishedDatasets.length }}</strong><small>可选数据集</small></span><span><strong>{{ conversations.length }}</strong><small>历史会话</small></span></div>
    </section>

    <UnifiedState v-if="loading" state="loading" description="正在加载 AI 会话和授权数据集。" />
    <UnifiedState v-else-if="loadError && !conversations.length" state="error" :description="loadError" retryable @retry="load" />
    <section v-else class="ai-analysis-workbench">
      <aside class="ai-history-panel" aria-label="AI 历史对话">
        <header><div><span class="eyebrow">HISTORY</span><strong>历史对话</strong></div><el-button type="primary" :icon="Plus" circle aria-label="新建对话" @click="newDialog = true" /></header>
        <el-input v-model="conversationKeyword" clearable placeholder="搜索对话" />
        <div class="ai-history-list">
          <button v-for="item in filteredConversations" :key="item.id" type="button" :class="{ active: item.id === active?.id }" @click="open(item.id)"><span>{{ item.title }}</span><small>{{ formatTime(item.updatedAt) }} · 上下文 v{{ item.contextVersion }}</small></button>
          <UnifiedState v-if="!filteredConversations.length" state="empty" title="暂无对话" description="创建会话后开始分析。" />
        </div>
      </aside>

      <main class="ai-main-panel">
        <template v-if="active">
          <header class="ai-main-heading"><div><span class="eyebrow">{{ workspaceName }}</span><h3>{{ active.title }}</h3><p>会话仅绑定当前工作空间，切换企业或工作空间会立即中止事件流。</p></div><div><el-tag :type="busy ? 'warning' : taskStatus === 'SUCCEEDED' ? 'success' : 'info'">{{ statusLabel }}</el-tag><el-button v-if="evidence.length" text type="primary" @click="evidenceOpen = true">查看查询证据</el-button></div></header>

          <section class="ai-scope-bar">
            <div><label>工作空间</label><el-input :model-value="workspaceName" disabled /></div>
            <div><label>分析范围</label><el-select v-model="scopeMode" :disabled="busy"><el-option label="全部授权语义资产" value="WORKSPACE" /><el-option label="指定数据集" value="DATASET" /></el-select></div>
            <div><label>数据集</label><el-select v-model="selectedDatasetId" filterable clearable :disabled="busy || scopeMode !== 'DATASET'" placeholder="选择已发布数据集"><el-option v-for="dataset in publishedDatasets" :key="dataset.id" :label="dataset.name" :value="dataset.id" /></el-select></div>
            <div class="ai-scope-note"><span>安全范围</span><strong>{{ scopeDescription }}</strong></div>
          </section>

          <div ref="messageScroller" class="ai-message-stream" aria-live="polite">
            <article v-for="message in messages" :key="message.id" class="ai-chat-message" :class="message.role.toLowerCase()"><div class="ai-avatar">{{ message.role === 'USER' ? app.userInitial : 'AI' }}</div><div><header><strong>{{ message.role === 'USER' ? '你' : 'AI 分析助手' }}</strong><time>{{ formatTime(message.createdAt) }}</time></header><p>{{ message.content }}</p></div></article>

            <article v-if="result" class="ai-result-card">
              <header><div><span class="eyebrow">VERIFIED RESULT</span><h3>{{ result.answer.summary }}</h3></div><el-tag type="success" effect="plain">真实查询结果</el-tag></header>
              <div class="ai-answer-grid">
                <section class="facts"><div class="ai-answer-section-title"><span>01</span><strong>事实</strong></div><div v-if="result.answer.facts.length" class="ai-fact-list"><article v-for="fact in result.answer.facts" :key="`${fact.query_id}-${fact.statement}`"><span>{{ fact.statement }}</span><strong>{{ String(fact.value ?? '—') }}<small v-if="fact.unit"> {{ fact.unit }}</small></strong><small>query_id · {{ fact.query_id }}</small></article></div><p v-else class="ai-no-data">当前权限和筛选范围内没有可用于回答的数值记录。</p></section>
                <section><div class="ai-answer-section-title"><span>02</span><strong>假设</strong></div><ul v-if="result.answer.assumptions.length"><li v-for="item in result.answer.assumptions" :key="item">{{ item }}</li></ul><p v-else>本次回答没有额外假设。</p></section>
                <section><div class="ai-answer-section-title"><span>03</span><strong>建议</strong></div><ul v-if="result.answer.recommendations.length"><li v-for="item in result.answer.recommendations" :key="item">{{ item }}</li></ul><p v-else>当前结果没有生成行动建议。</p></section>
                <section v-if="result.answer.limitations.length" class="limitations"><div class="ai-answer-section-title"><span>!</span><strong>限制</strong></div><ul><li v-for="item in result.answer.limitations" :key="item">{{ item }}</li></ul></section>
              </div>
              <div v-if="chartPresentation" class="ai-safe-chart"><header><strong>安全图表</strong><span>ChartSpec 已映射到前端白名单，不执行模型脚本或 HTML。</span></header><ChartPreview :spec="chartPresentation.spec" :result="chartPresentation.result" :loading="false" error="" /></div>
              <footer><span>这个回答有帮助吗？</span><el-button size="small" :type="feedbackRating === 1 ? 'success' : ''" :disabled="feedbackSending" @click="sendFeedback(1)">有帮助</el-button><el-button size="small" :type="feedbackRating === 0 ? 'danger' : ''" :disabled="feedbackSending" @click="openFeedback(0)">需改进</el-button><el-button text type="primary" @click="evidenceOpen = true">证据与版本</el-button></footer>
            </article>

            <section v-if="clarification" class="ai-clarification-card">
              <header><span>需要澄清</span><el-tag type="warning">{{ clarificationKindLabel }}</el-tag></header>
              <h3>{{ clarification.question }}</h3><p>请选择一个明确口径。恢复任务前服务端会重新检查当前权限和会话版本。</p>
              <div><button v-for="(option, index) in clarification.options" :key="option.metric_version_id ?? option.value ?? index" type="button" :disabled="resuming" @click="resume(option)"><strong>{{ option.name ?? option.label ?? option.value }}</strong><span v-if="option.unit">单位：{{ option.unit }}</span><small v-if="option.description">{{ option.description }}</small></button></div>
            </section>

            <el-alert v-if="taskError" type="error" :title="taskError" show-icon :closable="false" />
          </div>

          <form class="ai-question-composer" @submit.prevent="ask">
            <el-input v-model="question" type="textarea" :rows="3" maxlength="4000" show-word-limit resize="none" :disabled="busy || submitting" placeholder="例如：最近 30 天各地区净销售额是多少？" />
            <div><span>不会执行任意 SQL；无权限、无数据或口径不明确时会明确说明。</span><div><el-button v-if="canRetry" :icon="Refresh" :disabled="busy || submitting" @click="retryLast">重试上一问题</el-button><el-button v-if="busy && activeTaskId" type="danger" plain :loading="cancelling" @click="cancel">停止</el-button><el-button type="primary" native-type="submit" :loading="submitting" :disabled="busy || !question.trim() || (scopeMode === 'DATASET' && !selectedDatasetId)">发送问题</el-button></div></div>
          </form>
        </template>
        <UnifiedState v-else state="empty" title="开始第一次智能分析" description="新建会话后，用中文提出指标、趋势或业务对比问题。" action-text="新建会话" @action="newDialog = true" />
      </main>

      <aside class="ai-task-panel">
        <header><span class="eyebrow">TASK TRACE</span><strong>任务轨迹</strong></header>
        <div v-if="activeTaskId" class="ai-task-identity"><span>task_id</span><code>{{ activeTaskId }}</code><small>{{ reconnecting ? `SSE 第 ${reconnectAttempt} 次恢复中` : 'SSE 已连接或任务已结束' }}</small></div>
        <div class="ai-event-timeline">
          <article v-for="item in eventTimeline" :key="item.id" :class="item.tone"><span /><div><strong>{{ item.label }}</strong><small>{{ item.detail }}</small></div></article>
          <UnifiedState v-if="!eventTimeline.length" state="empty" title="等待任务" description="发送问题后展示 accepted、progress、clarification、result、error 和 done。" />
        </div>
        <div class="ai-task-help"><strong>结果边界</strong><p>事实来自 QueryPlan 查询；假设和建议单独呈现。任务恢复、证据读取和反馈都会再次鉴权。</p></div>
      </aside>
    </section>

    <CommonDialog v-model="newDialog" title="新建 AI 分析会话" width="520px">
      <el-form label-position="top"><el-form-item label="当前工作空间"><el-input :model-value="workspaceName" disabled /></el-form-item><el-form-item label="会话标题"><el-input v-model.trim="newTitle" maxlength="200" placeholder="例如：十月经营复盘" @keyup.enter="createConversation" /></el-form-item></el-form>
      <template #footer><el-button @click="newDialog = false">取消</el-button><el-button type="primary" :loading="creatingConversation" :disabled="!newTitle.trim()" @click="createConversation">创建</el-button></template>
    </CommonDialog>

    <CommonDialog v-model="feedbackOpen" title="结果反馈" width="560px">
      <p class="ai-feedback-hint">请说明结果需要改进的地方。反馈不改变本次事实数据。</p><el-input v-model="feedbackComment" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="例如：时间范围说明不够清楚" />
      <template #footer><el-button @click="feedbackOpen = false">取消</el-button><el-button type="primary" :loading="feedbackSending" @click="sendFeedback(0, feedbackComment)">提交反馈</el-button></template>
    </CommonDialog>

    <el-drawer v-model="evidenceOpen" title="查询证据与版本" size="520px" class="ai-evidence-drawer">
      <el-alert type="info" title="证据由 Spring Boot 在当前成员权限下重新鉴权后返回。" show-icon :closable="false" />
      <article v-for="(item, index) in evidence" :key="item.query_id ?? index" class="ai-evidence-card"><header><strong>证据 {{ index + 1 }}</strong><el-tag effect="plain">{{ item.query_id ?? '无查询 ID' }}</el-tag></header><dl><div v-for="row in evidenceSummary(item)" :key="row.label"><dt>{{ row.label }}</dt><dd>{{ row.value }}</dd></div></dl></article>
      <UnifiedState v-if="!evidence.length" state="empty" title="暂无查询证据" description="结果完成后将展示指标版本、数据版本、发布批次和追踪 ID。" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import type { Id } from '../api/resource-id'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChartPreview from '../components/common/ChartPreview.vue'
import CommonDialog from '../components/common/CommonDialog.vue'
import UnifiedState from '../components/common/UnifiedState.vue'
import { aiApi, semanticApi } from '../api/runtime'
import { ApiClientError } from '../api/client'
import { evidenceSummary, taskFailureMessage, toChartPresentation } from '../ai/presentation'
import { AiEventStream } from '../ai/sse'
import type { AiClarification, AiConversation, AiEvidence, AiMessage, AiResultPayload, AiTaskEvent } from '../ai/types'
import type { Dataset } from '../semantic/types'
import { useAppStore } from '../stores/app'

interface TimelineItem { id: string; label: string; detail: string; tone: 'active' | 'success' | 'warning' | 'error' | 'muted' }
type ClarificationOption = AiClarification['options'][number]

const app = useAppStore()
const conversations = ref<AiConversation[]>([])
const datasets = ref<Dataset[]>([])
const active = ref<AiConversation>()
const messages = ref<AiMessage[]>([])
const loading = ref(true)
const loadError = ref('')
const conversationKeyword = ref('')
const question = ref('')
const scopeMode = ref<'WORKSPACE' | 'DATASET'>('WORKSPACE')
const selectedDatasetId = ref<Id>()
const busy = ref(false)
const submitting = ref(false)
const resuming = ref(false)
const cancelling = ref(false)
const activeTaskId = ref<string>()
const taskStatus = ref('IDLE')
const taskError = ref('')
const result = ref<AiResultPayload>()
const clarification = ref<AiClarification>()
const evidence = ref<AiEvidence[]>([])
const eventTimeline = ref<TimelineItem[]>([])
const reconnecting = ref(false)
const reconnectAttempt = ref(0)
const lastQuestion = ref('')
let pendingSubmissionKey: string | undefined
let streamController: AbortController | undefined
const newDialog = ref(false)
const newTitle = ref('')
const creatingConversation = ref(false)
const evidenceOpen = ref(false)
const feedbackOpen = ref(false)
const feedbackComment = ref('')
const feedbackSending = ref(false)
const feedbackRating = ref<0 | 1>()
const messageScroller = ref<HTMLElement>()

const workspaceName = computed(() => app.workspaces.find(item => item.id === app.workspaceId)?.workspaceName ?? '当前工作空间')
const publishedDatasets = computed(() => datasets.value.filter(item => Boolean(item.currentVersionId)))
const filteredConversations = computed(() => conversations.value.filter(item => !conversationKeyword.value || item.title.toLowerCase().includes(conversationKeyword.value.toLowerCase())))
const scopeDescription = computed(() => scopeMode.value === 'DATASET' ? publishedDatasets.value.find(item => item.id === selectedDatasetId.value)?.name ?? '尚未选择数据集' : '当前工作空间全部已授权发布资产')
const chartPresentation = computed(() => toChartPresentation(result.value))
const statusLabel = computed(() => ({ IDLE: '可提问', ACCEPTED: '已接受', RUNNING: '分析中', WAITING_CLARIFICATION: '待澄清', SUCCEEDED: '已完成', PARTIAL_SUCCESS: '部分完成', FAILED: '失败', TIMEOUT: '超时', CANCEL_REQUESTED: '取消中', CANCELLED: '已取消' } as Record<string, string>)[taskStatus.value] ?? taskStatus.value)
const clarificationKindLabel = computed(() => ({ METRIC: '指标口径', TIME: '时间范围', BUSINESS: '业务口径' } as Record<string, string>)[clarification.value?.kind ?? 'METRIC'] ?? '业务口径')
const canRetry = computed(() => Boolean(lastQuestion.value && ['FAILED', 'TIMEOUT', 'CANCELLED', 'ERROR'].includes(taskStatus.value)))

async function load(): Promise<void> {
  loading.value = true; loadError.value = ''
  const [conversationResult, datasetResult] = await Promise.allSettled([aiApi.listConversations(), semanticApi.datasets(app.workspaceId)])
  if (conversationResult.status === 'fulfilled') conversations.value = conversationResult.value.filter(item => item.workspaceId === app.workspaceId)
  if (datasetResult.status === 'fulfilled') datasets.value = datasetResult.value
  const failure = [conversationResult, datasetResult].find(item => item.status === 'rejected') as PromiseRejectedResult | undefined
  if (failure) loadError.value = failure.reason instanceof Error ? failure.reason.message : 'AI 工作台加载失败'
  loading.value = false
  if (conversations.value[0]) await open(conversations.value[0].id)
}

async function open(id: Id): Promise<void> {
  stopStream(); resetTaskView()
  try {
    active.value = await aiApi.getConversation(id)
    messages.value = await aiApi.messages(id)
    lastQuestion.value = [...messages.value].reverse().find(item => item.role === 'USER')?.content ?? ''
    const latest = [...messages.value].reverse().find(item => item.taskId)
    if (latest?.taskId) await restoreTask(String(latest.taskId))
    await scrollToBottom()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '会话加载失败') }
}

async function restoreTask(taskId: string): Promise<void> {
  const task = await aiApi.task(taskId)
  activeTaskId.value = taskId; taskStatus.value = task.status
  busy.value = ['ACCEPTED', 'RUNNING', 'CANCEL_REQUESTED'].includes(task.status)
  if (['SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED', 'TIMEOUT', 'WAITING_CLARIFICATION'].includes(task.status)) sessionStorage.removeItem(`aibi.ai.lastEvent.${taskId}`)
  connect(taskId)
}

async function createConversation(): Promise<void> {
  if (!newTitle.value.trim() || creatingConversation.value) return
  creatingConversation.value = true
  try {
    const created = await aiApi.createConversation(app.workspaceId, newTitle.value.trim())
    conversations.value.unshift(created); newDialog.value = false; newTitle.value = ''; await open(created.id)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '会话创建失败') }
  finally { creatingConversation.value = false }
}

async function ask(): Promise<void> {
  const content = question.value.trim()
  if (!content || !active.value || busy.value || submitting.value) return
  if (scopeMode.value === 'DATASET' && !selectedDatasetId.value) { ElMessage.warning('请选择已发布数据集'); return }
  question.value = ''
  await submitQuestion(content, crypto.randomUUID())
}

async function submitQuestion(content: string, requestKey: string): Promise<void> {
  if (!active.value) return
  submitting.value = true; busy.value = true; taskError.value = ''; taskStatus.value = 'ACCEPTED'; result.value = undefined; evidence.value = []; clarification.value = undefined; feedbackRating.value = undefined
  pendingSubmissionKey = requestKey; lastQuestion.value = content
  eventTimeline.value = [{ id: `local-${Date.now()}`, label: 'accepted', detail: '问题已提交，等待服务端返回 task_id', tone: 'active' }]
  const optimisticId = `local-message-${Date.now()}`
  messages.value.push({ id: optimisticId, conversationId: active.value.id, sequence: messages.value.length + 1, role: 'USER', content, createdAt: new Date().toISOString() })
  await scrollToBottom()
  try {
    const accepted = await aiApi.ask(active.value.id, content, active.value.contextVersion, scopeMode.value === 'DATASET' ? selectedDatasetId.value : undefined, requestKey)
    activeTaskId.value = accepted.taskId; taskStatus.value = accepted.status || 'ACCEPTED'; pendingSubmissionKey = undefined
    addTimeline(`accepted-${accepted.taskId}`, 'accepted', `task_id ${accepted.taskId} 已创建`, 'active')
    connect(accepted.taskId)
  } catch (error) {
    busy.value = false; taskStatus.value = 'ERROR'
    const apiError = error instanceof ApiClientError ? error : undefined
    taskError.value = apiError ? taskFailureMessage(apiError.code, apiError.message) : error instanceof Error ? error.message : '问题提交失败'
    addTimeline(`submit-error-${Date.now()}`, 'error', taskError.value, 'error')
  } finally { submitting.value = false }
}

function connect(taskId: string): void {
  stopStream(); streamController = new AbortController(); reconnecting.value = false; reconnectAttempt.value = 0
  const stream = new AiEventStream(aiApi, taskId)
  void stream.connect(streamController.signal, {
    onEvent: event => handleEvent(taskId, event),
    onReconnect: attempt => { reconnecting.value = true; reconnectAttempt.value = attempt; addTimeline(`reconnect-${attempt}`, 'progress', `SSE 连接中断，正在第 ${attempt} 次恢复`, 'warning') },
  })
}

function handleEvent(taskId: string, event: AiTaskEvent): void {
  reconnecting.value = false
  if (event.type === 'accepted') {
    taskStatus.value = 'ACCEPTED'; busy.value = true; addTimeline(event.id, 'accepted', '任务已进入执行队列', 'active')
  } else if (event.type === 'progress') {
    taskStatus.value = 'RUNNING'; busy.value = true
    const stage = String(event.data.stage ?? 'running'); addTimeline(event.id, 'progress', stageLabel(stage), 'active')
  } else if (event.type === 'clarification') {
    clarification.value = normalizeClarification(event.data); taskStatus.value = 'WAITING_CLARIFICATION'; busy.value = false
    addTimeline(event.id, 'clarification', clarification.value.question, 'warning')
  } else if (event.type === 'result') {
    result.value = event.data as unknown as AiResultPayload; evidence.value = result.value.evidence ?? []
    addTimeline(event.id, 'result', result.value.answer.summary, 'success')
  } else if (event.type === 'error') {
    const code = String(event.data.code ?? 'AI_GRAPH_FAILED'); taskError.value = taskFailureMessage(code, String(event.data.message ?? '智能分析失败'))
    addTimeline(event.id, 'error', taskError.value, 'error')
  } else if (event.type === 'done') {
    taskStatus.value = String(event.data.status ?? 'FAILED'); busy.value = false
    addTimeline(event.id, 'done', doneLabel(taskStatus.value), ['SUCCEEDED', 'PARTIAL_SUCCESS'].includes(taskStatus.value) ? 'success' : taskStatus.value === 'CANCELLED' ? 'muted' : 'error')
    stopStream()
    if (['SUCCEEDED', 'PARTIAL_SUCCESS'].includes(taskStatus.value)) void finishSuccess(taskId)
  }
  void scrollToBottom()
}

async function finishSuccess(taskId: string): Promise<void> {
  try { evidence.value = await aiApi.evidence(taskId) } catch { /* Event evidence remains available. */ }
  if (active.value) {
    active.value = await aiApi.getConversation(active.value.id)
    messages.value = await aiApi.messages(active.value.id)
  }
  await scrollToBottom()
}

async function resume(option: ClarificationOption): Promise<void> {
  if (!active.value || !activeTaskId.value || resuming.value) return
  const payload = option.payload ?? (option.metric_version_id ? { metric_version_id: option.metric_version_id } : { [clarification.value?.field ?? 'selection']: option.value ?? option.name ?? option.label ?? '' })
  resuming.value = true; busy.value = true; taskError.value = ''
  addTimeline(`resume-${Date.now()}`, 'accepted', '澄清已提交，正在重新校验权限', 'active')
  try {
    await aiApi.resume(activeTaskId.value, payload, active.value.contextVersion)
    active.value = await aiApi.getConversation(active.value.id); clarification.value = undefined; taskStatus.value = 'RUNNING'; connect(activeTaskId.value)
  } catch (error) { busy.value = false; taskStatus.value = 'ERROR'; taskError.value = error instanceof Error ? error.message : '任务恢复失败' }
  finally { resuming.value = false }
}

async function cancel(): Promise<void> {
  if (!activeTaskId.value || cancelling.value) return
  cancelling.value = true
  try { const task = await aiApi.cancel(activeTaskId.value); taskStatus.value = task.status; addTimeline(`cancel-${Date.now()}`, 'progress', `取消请求状态：${task.status}`, 'warning') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '取消任务失败') }
  finally { cancelling.value = false }
}

async function retryLast(): Promise<void> {
  if (!lastQuestion.value || busy.value || submitting.value) return
  await submitQuestion(lastQuestion.value, pendingSubmissionKey ?? crypto.randomUUID())
}

function openFeedback(rating: 0 | 1): void { feedbackRating.value = rating; feedbackComment.value = ''; feedbackOpen.value = true }
async function sendFeedback(rating: 0 | 1, comment?: string): Promise<void> {
  if (!activeTaskId.value || feedbackSending.value) return
  feedbackSending.value = true
  try { await aiApi.feedback(activeTaskId.value, rating, comment?.trim()); feedbackRating.value = rating; feedbackOpen.value = false; ElMessage.success('感谢反馈') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '反馈提交失败') }
  finally { feedbackSending.value = false }
}

function normalizeClarification(data: Record<string, unknown>): AiClarification {
  return {
    code: String(data.code ?? 'SEMANTIC_AMBIGUITY'),
    question: String(data.question ?? '请选择需要使用的业务口径'),
    context_version: Number(data.context_version ?? active.value?.contextVersion ?? 0),
    kind: (['METRIC', 'TIME', 'BUSINESS'].includes(String(data.kind)) ? String(data.kind) : 'METRIC') as NonNullable<AiClarification['kind']>,
    ...(typeof data.field === 'string' ? { field: data.field } : {}),
    options: Array.isArray(data.options) ? data.options as AiClarification['options'] : [],
  }
}

function addTimeline(id: string, label: string, detail: string, tone: TimelineItem['tone']): void {
  const existing = eventTimeline.value.find(item => item.id === id)
  if (existing) Object.assign(existing, { label, detail, tone })
  else eventTimeline.value.push({ id, label, detail, tone })
}
function stageLabel(stage: string): string { return ({ admission: '检查身份、额度与任务时限', intent: '识别中文问题意图', retrieve: '检索授权指标、数据集与业务术语', clarify: '核对指标、时间和业务口径', resume_guard: '恢复前重新校验权限', plan: '生成强类型查询计划', preflight: '安全预检查询计划', query: '执行受控真实数据查询', result_check: '校验结果完整性与数据版本', chart: '生成安全 ChartSpec', answer: '分离事实、假设和建议' } as Record<string, string>)[stage] ?? '正在执行智能分析' }
function doneLabel(status: string): string { return ({ SUCCEEDED: '任务完成，答案与证据已就绪', PARTIAL_SUCCESS: '任务部分完成，请查看限制说明', FAILED: '任务失败，未生成可信答案', TIMEOUT: '任务超时，已停止继续执行', CANCELLED: '任务已取消' } as Record<string, string>)[status] ?? `任务结束：${status}` }
function resetTaskView(): void { busy.value = false; submitting.value = false; activeTaskId.value = undefined; taskStatus.value = 'IDLE'; taskError.value = ''; result.value = undefined; clarification.value = undefined; evidence.value = []; eventTimeline.value = []; reconnecting.value = false; feedbackRating.value = undefined; pendingSubmissionKey = undefined }
function stopStream(): void { streamController?.abort(); streamController = undefined }
async function scrollToBottom(): Promise<void> { await nextTick(); if (messageScroller.value) messageScroller.value.scrollTop = messageScroller.value.scrollHeight }
function formatTime(value: string): string { return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }

watch(() => app.contextVersion, () => { stopStream(); active.value = undefined; messages.value = []; void load() })
onBeforeUnmount(stopStream)
void load()
</script>
