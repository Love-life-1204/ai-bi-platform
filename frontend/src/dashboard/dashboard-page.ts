import { optionalResourceId, resourceId } from '../api/resource-id'
import type { Id } from '../api/resource-id'
import { ApiClientError } from '../api/client'
import type { QueryFilter, QueryPlan } from '../query/types'
import { DashboardApi } from './api'
import { ChartView } from './chart-view'
import type {
  Chart, ChartData, ChartSpec, Dashboard, DashboardData, DashboardQueryInput,
  DashboardVersion, GlobalFilterDefinition, WidgetResult,
} from './types'

export class DashboardPage {
  private charts: Chart[] = []
  private dashboards: Dashboard[] = []
  private views: ChartView[] = []
  private active?: DashboardData
  private previewDraft = false
  private filters: QueryFilter[] = []
  private interactionFilters: QueryFilter[] = []

  constructor(
    private readonly root: HTMLElement,
    private readonly api: DashboardApi,
    private readonly workspaceId: Id,
  ) {}

  async load(): Promise<void> {
    this.clearViews(); this.root.replaceChildren(this.message('正在加载图表与看板…'))
    ;[this.charts, this.dashboards] = await Promise.all([
      this.api.listCharts(this.workspaceId), this.api.listDashboards(this.workspaceId),
    ])
    this.renderList()
  }

  destroy(): void {
    this.clearViews()
    this.root.replaceChildren()
  }

  private renderList(): void {
    this.clearViews(); this.root.replaceChildren()
    const header = document.createElement('header'); header.className = 'ds-header'
    header.innerHTML = '<div><h1>图表与看板</h1><p>从已发布指标创建图表，编排为可筛选、可联动的看板。</p></div>'
    const actions = document.createElement('div'); actions.className = 'actions'
    actions.append(this.button('新建图表', 'secondary', () => this.renderChartForm()),
      this.button('新建看板', 'primary', () => this.renderDashboardForm()))
    header.append(actions); this.root.append(header, this.chartSection(), this.dashboardSection())
  }

  private chartSection(): HTMLElement {
    const section = document.createElement('section'); section.innerHTML = '<h2 class="section-title">图表</h2>'
    if (!this.charts.length) { section.append(this.message('还没有图表。')); return section }
    const table = this.table(['名称', '编码', '状态', '版本', '操作']); const body = table.createTBody()
    this.charts.forEach(chart => {
      const row = body.insertRow()
      row.append(this.cell(chart.name), this.cell(chart.code), this.cell(chart.status),
        this.cell(chart.currentVersionId ? String(chart.currentVersionId) : '未发布'))
      const action = row.insertCell(); action.className = 'actions'
      action.append(this.button('编辑', 'ghost', async () => this.renderChartForm(await this.api.getChart(chart.id))),
        this.button('校验', 'ghost', async () => {
        const result = await this.api.validateChart(chart.id)
        globalThis.alert(result.valid ? '图表配置校验通过' : '图表配置校验失败')
      }), this.button('发布', 'primary', async () => { await this.api.publishChart(chart.id); await this.load() }))
    })
    section.append(table); return section
  }

  private dashboardSection(): HTMLElement {
    const section = document.createElement('section'); section.innerHTML = '<h2 class="section-title">看板</h2>'
    if (!this.dashboards.length) { section.append(this.message('还没有看板。')); return section }
    const table = this.table(['名称', '编码', '状态', '操作']); const body = table.createTBody()
    this.dashboards.forEach(dashboard => {
      const row = body.insertRow()
      row.append(this.cell(dashboard.name), this.cell(dashboard.code), this.cell(dashboard.status))
      const action = row.insertCell(); action.className = 'actions'
      action.append(this.button('打开', 'primary', () => void this.openDashboard(dashboard.id)))
    })
    section.append(table); return section
  }

  private renderChartForm(existing?: ChartData): void {
    this.root.replaceChildren(); const form = document.createElement('form')
    form.className = 'ds-form-card semantic-form'; form.innerHTML = `
      <header><div><h1>${existing ? '编辑图表' : '新建图表'}</h1><p>图表配置只接受安全的声明式字段，不执行脚本或 HTML。</p></div></header>
      <div class="form-grid">
        <label>图表编码<input name="code" required pattern="[a-z][a-z0-9_]{1,63}"></label>
        <label>名称<input name="name" required></label>
        <label>类型<select name="type"><option>KPI</option><option>BAR</option><option>LINE</option><option>PIE</option><option>STACKED_BAR</option><option>TABLE</option></select></label>
        <label>数据集版本 ID<input name="datasetVersionId" type="number" min="1" required></label>
        <label>指标版本 ID（逗号分隔）<input name="metricVersionIds" required></label>
        <label>维度字段 ID（逗号分隔）<input name="dimensionFieldIds"></label>
        <label>分类字段键<input name="categoryField" placeholder="dimension_101"></label>
        <label>数值字段键（逗号分隔）<input name="valueFields" required placeholder="metric_701"></label>
        <label>联动字段 ID<input name="interactionFieldId" type="number" min="1"></label>
        <label>查询行数<input name="limit" type="number" min="1" max="10000" value="500"></label>
      </div>
      <label class="json-field">明细列键（逗号分隔）<input name="detailColumns"></label>
      <footer><button type="button" class="button ghost" data-cancel>取消</button><button class="button primary">保存草稿</button></footer>`
    form.querySelector('[data-cancel]')!.addEventListener('click', () => this.renderList())
    if (existing) {
      const version = existing.draft ?? existing.current
      const set = (name: string, value: string) => { const input = form.elements.namedItem(name) as HTMLInputElement | HTMLSelectElement | null; if (input) input.value = value }
      set('code', existing.chart.code); (form.elements.namedItem('code') as HTMLInputElement).disabled = true
      set('name', existing.chart.name)
      if (version) {
        set('type', version.chartSpec.type); set('datasetVersionId', String(version.datasetVersionId))
        set('metricVersionIds', version.queryPlan.metricVersionIds.join(',')); set('dimensionFieldIds', version.queryPlan.dimensionFieldIds.join(','))
        set('categoryField', version.chartSpec.categoryField ?? ''); set('valueFields', version.chartSpec.valueFields.join(','))
        set('interactionFieldId', String(version.chartSpec.interactionFieldId ?? '')); set('limit', String(version.queryPlan.limit))
        set('detailColumns', (version.chartSpec.detailColumns ?? []).join(','))
      }
    }
    form.addEventListener('submit', event => void this.saveChart(event, form, existing)); this.root.append(form)
  }

  private async saveChart(event: SubmitEvent, form: HTMLFormElement, existing?: ChartData): Promise<void> {
    event.preventDefault(); const data = new FormData(form)
    const list = (name: string) => String(data.get(name) ?? '').split(',').map(value => value.trim()).filter(Boolean)
    const numbers = (name: string) => list(name).map(resourceId)
    const datasetVersionId = resourceId(data.get('datasetVersionId'))
    const type = String(data.get('type')) as ChartSpec['type']
    const categoryField = String(data.get('categoryField') ?? '').trim()
    const interactionFieldId = optionalResourceId(data.get('interactionFieldId'))
    const detailColumns = list('detailColumns')
    const plan: QueryPlan = {
      datasetVersionId, metricVersionIds: numbers('metricVersionIds'),
      dimensionFieldIds: numbers('dimensionFieldIds'), filters: [], sort: [], limit: Number(data.get('limit')),
    }
    const spec: ChartSpec = {
      type, valueFields: list('valueFields'),
      ...(categoryField ? { categoryField } : {}),
      ...(interactionFieldId ? { interactionFieldId } : {}),
      ...(detailColumns.length ? { detailColumns } : {}),
    }
    if (existing) {
      await this.api.updateChart(existing.chart.id, { name: String(data.get('name')), datasetVersionId,
        queryPlan: plan, chartSpec: spec, lockVersion: existing.chart.lockVersion })
    } else {
      await this.api.createChart({ workspaceId: this.workspaceId, code: String(data.get('code')),
        name: String(data.get('name')), datasetVersionId, queryPlan: plan, chartSpec: spec })
    }
    await this.load()
  }

  private renderDashboardForm(): void {
    const published = this.charts.filter(chart => chart.currentVersionId)
    this.root.replaceChildren(); const form = document.createElement('form'); form.className = 'ds-form-card'
    const options = published.map(chart => `<label class="chart-choice"><input type="checkbox" name="chart" value="${chart.currentVersionId}">${this.escape(chart.name)}</label>`).join('')
    form.innerHTML = `<header><div><h1>新建看板</h1><p>选择已发布图表，系统按 12 列网格生成初始布局。</p></div></header>
      <div class="form-grid"><label>看板编码<input name="code" required pattern="[a-z][a-z0-9_]{1,63}"></label><label>名称<input name="name" required></label></div>
      <fieldset class="chart-choices"><legend>图表组件</legend>${options || '<p>请先发布至少一个图表。</p>'}</fieldset>
      <label class="json-field">全局筛选定义 JSON<textarea name="filters" rows="5">[]</textarea><small>例如 [{"key":"region","label":"区域","fieldId":301,"type":"SELECT"}]</small></label>
      <footer><button type="button" class="button ghost" data-cancel>取消</button><button class="button primary">创建看板</button></footer>`
    form.querySelector('[data-cancel]')!.addEventListener('click', () => this.renderList())
    form.addEventListener('submit', event => void this.createDashboard(event, form)); this.root.append(form)
  }

  private async createDashboard(event: SubmitEvent, form: HTMLFormElement): Promise<void> {
    event.preventDefault(); const data = new FormData(form)
    const versions = data.getAll('chart').map(resourceId)
    const widgets = versions.map((chartVersionId, index) => ({
      key: `widget_${index + 1}`, chartVersionId,
      position: { x: (index % 2) * 6, y: Math.floor(index / 2) * 4, w: 6, h: 4 },
    }))
    let filters: GlobalFilterDefinition[]
    try { filters = JSON.parse(String(data.get('filters') ?? '[]')) as GlobalFilterDefinition[] }
    catch { globalThis.alert('全局筛选定义不是合法 JSON'); return }
    const created = await this.api.createDashboard({ workspaceId: this.workspaceId,
      code: String(data.get('code')), name: String(data.get('name')),
      layout: { columns: 12, rowHeight: 72 }, filters, widgets })
    await this.openDashboard(created.dashboard.id)
  }

  private async openDashboard(id: Id): Promise<void> {
    this.clearViews(); this.root.replaceChildren(this.message('正在打开看板…'))
    this.active = await this.api.getDashboard(id)
    this.previewDraft = !this.active.current && Boolean(this.active.draft)
    this.filters = []; this.interactionFilters = []
    await this.renderDashboard()
  }

  private async renderDashboard(): Promise<void> {
    const active = this.active; if (!active) return
    this.clearViews(); this.root.replaceChildren()
    const version = this.previewDraft ? active.draft : active.current
    if (!version) { this.root.append(this.message('没有可显示的看板版本。')); return }
    const header = document.createElement('header'); header.className = 'ds-header dashboard-header'
    const title = document.createElement('div'); title.innerHTML = `<h1>${this.escape(active.dashboard.name)}</h1><p>${this.previewDraft ? '草稿预览' : '正式发布版'} · 版本 ${version.versionNo}</p>`
    const actions = document.createElement('div'); actions.className = 'actions'
    actions.append(this.button('返回', 'ghost', () => void this.load()))
    if (active.draft && active.current) actions.append(this.button(this.previewDraft ? '查看正式版' : '预览草稿', 'secondary', () => { this.previewDraft = !this.previewDraft; void this.renderDashboard() }))
    if (active.draft) actions.append(this.button('发布', 'primary', async () => { await this.api.publishDashboard(active.dashboard.id); await this.openDashboard(active.dashboard.id) }))
    header.append(title, actions); this.root.append(header, this.filterBar(version))
    const grid = document.createElement('section'); grid.className = 'dashboard-grid'; grid.dataset.editable = String(this.previewDraft)
    version.widgets.forEach(widget => {
      const card = document.createElement('article'); card.className = 'dashboard-widget'; card.dataset.key = widget.key
      card.style.gridColumn = `${widget.position.x + 1} / span ${widget.position.w}`
      card.style.gridRow = `${widget.position.y + 1} / span ${widget.position.h}`
      card.draggable = this.previewDraft
      const heading = document.createElement('header'); heading.textContent = widget.key; card.append(heading)
      const body = document.createElement('div'); body.className = 'widget-body'; card.append(body); grid.append(card)
      const view = new ChartView(body, {
        onFilter: (_field, value) => {
          const found = this.latestWidgetResult(widget.key)?.chartSpec
          if (!found?.interactionFieldId) return
          this.interactionFilters = [{ fieldId: found.interactionFieldId, operator: 'EQ', value }]
          void this.refreshWidgets(version, grid)
        },
        onDrill: (field, value, row) => this.showDetail(field, value, row),
        onRetry: () => void this.refreshWidgets(version, grid),
      })
      view.loading(); this.views.push(view)
    })
    this.enableDrag(grid, version)
    this.root.append(grid)
    await this.refreshWidgets(version, grid)
  }

  private lastResults: WidgetResult[] = []
  private latestWidgetResult(key: string): WidgetResult | undefined { return this.lastResults.find(item => item.key === key) }

  private async refreshWidgets(version: DashboardVersion, grid: HTMLElement): Promise<void> {
    const input = this.queryInput(); this.views.forEach(view => view.loading())
    try {
      const response = await this.api.queryDashboard(version.dashboardId, input); this.lastResults = response.widgets
      version.widgets.forEach((widget, index) => {
        const value = response.widgets.find(item => item.key === widget.key); const view = this.views[index]
        if (!view || !value) return
        if (value.status === 'SUCCEEDED' && value.result && value.chartSpec) view.render(value.chartSpec, value.result)
        else view.error(value.error?.message ?? '图表加载失败', value.error?.retryable)
      })
      grid.querySelectorAll('.dashboard-widget').forEach(card => card.classList.remove('loading'))
    } catch (error) {
      const timeout = error instanceof ApiClientError && error.code === 'REQUEST_TIMEOUT'
      this.views.forEach(view => view.error(timeout ? '看板查询超时，请缩小时间范围后重试' : '看板加载失败', true))
    }
  }

  private queryInput(): DashboardQueryInput {
    const start = this.root.querySelector<HTMLInputElement>('[name="timeStart"]')?.value
    const end = this.root.querySelector<HTMLInputElement>('[name="timeEnd"]')?.value
    const fieldId = optionalResourceId(this.root.querySelector<HTMLInputElement>('[name="timeFieldId"]')?.value)
    const dimensionFilters = Array.from(this.root.querySelectorAll<HTMLInputElement>('[data-dimension-filter]'))
      .filter(input => input.value.trim())
      .map(input => {
        const values = input.value.split(',').map(value => value.trim()).filter(Boolean)
        const multi = input.dataset.filterType === 'MULTI_SELECT'
        return { fieldId: resourceId(input.dataset.fieldId), operator: multi ? 'IN' as const : 'EQ' as const,
          value: multi ? values : values[0] }
      })
    this.filters = dimensionFilters
    return {
      ...(start && end && fieldId ? { timeRange: { fieldId, start: new Date(start).toISOString(), end: new Date(end).toISOString() } } : {}),
      filters: this.filters, interactionFilters: this.interactionFilters, previewDraft: this.previewDraft,
    }
  }

  private filterBar(version: DashboardVersion): HTMLElement {
    const bar = document.createElement('section'); bar.className = 'dashboard-filters'
    const timeDefinition = version.filters.find(filter => filter.type === 'DATE_RANGE')
    bar.innerHTML = `<label>时间字段 ID<input name="timeFieldId" type="number" min="1" value="${timeDefinition?.fieldId ?? ''}"></label><label>开始<input name="timeStart" type="datetime-local"></label><label>结束<input name="timeEnd" type="datetime-local"></label>`
    version.filters.filter(filter => filter.type !== 'DATE_RANGE').forEach(filter => {
      const label = document.createElement('label'); label.textContent = filter.label
      const input = document.createElement('input'); input.dataset.dimensionFilter = 'true'
      input.dataset.fieldId = String(filter.fieldId); input.dataset.filterType = filter.type
      input.placeholder = filter.type === 'MULTI_SELECT' ? '多个值用逗号分隔' : '筛选值'
      label.append(input); bar.append(label)
    })
    const refresh = this.button('应用筛选', 'primary', () => {
      const grid = this.root.querySelector<HTMLElement>('.dashboard-grid'); if (grid) void this.refreshWidgets(version, grid)
    })
    const reset = this.button('清除联动', 'ghost', () => { this.interactionFilters = []; const grid = this.root.querySelector<HTMLElement>('.dashboard-grid'); if (grid) void this.refreshWidgets(version, grid) })
    bar.append(refresh, reset); return bar
  }

  private enableDrag(grid: HTMLElement, version: DashboardVersion): void {
    if (!this.previewDraft || !this.active?.draft) return
    let dragging = ''
    grid.addEventListener('dragstart', event => { dragging = (event.target as HTMLElement).closest<HTMLElement>('.dashboard-widget')?.dataset.key ?? '' })
    grid.addEventListener('dragover', event => event.preventDefault())
    grid.addEventListener('drop', event => {
      event.preventDefault(); if (!dragging) return
      const bounds = grid.getBoundingClientRect(); const column = Math.max(0, Math.min(11, Math.floor((event.clientX - bounds.left) / bounds.width * 12)))
      const target = version.widgets.find(widget => widget.key === dragging); if (!target) return
      target.position.x = Math.min(column, 12 - target.position.w)
      target.position.y = Math.max(0, Math.floor((event.clientY - bounds.top) / 72))
      void this.saveLayout(version)
    })
  }

  private async saveLayout(version: DashboardVersion): Promise<void> {
    const active = this.active; if (!active?.draft) return
    const updated = await this.api.updateDashboard(active.dashboard.id, {
      name: active.dashboard.name, layout: { columns: 12, rowHeight: version.layout.rowHeight ?? 72 },
      filters: version.filters, widgets: version.widgets.map(widget => ({ key: widget.key,
        chartVersionId: widget.chartVersionId, position: widget.position })), lockVersion: active.dashboard.lockVersion,
    })
    this.active = updated; this.previewDraft = true; await this.renderDashboard()
  }

  private showDetail(field: string, value: unknown, row: Record<string, unknown>): void {
    const dialog = document.createElement('dialog'); dialog.className = 'detail-dialog'
    const heading = document.createElement('h2'); heading.textContent = `明细 · ${field} = ${String(value ?? '')}`
    const table = this.table(['字段', '值']); const body = table.createTBody()
    Object.entries(row).forEach(([key, cellValue]) => { const record = body.insertRow(); record.append(this.cell(key), this.cell(String(cellValue ?? ''))) })
    const close = this.button('关闭', 'primary', () => dialog.close()); dialog.append(heading, table, close)
    dialog.addEventListener('close', () => dialog.remove()); document.body.append(dialog); dialog.showModal()
  }

  private clearViews(): void { this.views.forEach(view => view.destroy()); this.views = [] }
  private message(text: string): HTMLElement { const value = document.createElement('div'); value.className = 'ds-empty'; value.textContent = text; return value }
  private cell(text: string): HTMLTableCellElement { const value = document.createElement('td'); value.textContent = text; return value }
  private table(headers: string[]): HTMLTableElement { const table = document.createElement('table'); table.className = 'ds-table'; const row = table.createTHead().insertRow(); headers.forEach(value => { const cell = document.createElement('th'); cell.textContent = value; row.append(cell) }); return table }
  private button(text: string, style: string, action: () => void): HTMLButtonElement { const value = document.createElement('button'); value.type = 'button'; value.className = `button ${style}`; value.textContent = text; value.addEventListener('click', action); return value }
  private escape(value: string): string { const node = document.createElement('span'); node.textContent = value; return node.innerHTML }
}
