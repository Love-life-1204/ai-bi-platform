import { resourceId } from '../api/resource-id'
import type { Id } from '../api/resource-id'
import { SemanticApi } from './api'
import type {
  Dataset,
  DatasetField,
  DatasetInput,
  Metric,
  MetricInput,
  PreviewData,
  ValidationResult,
} from './types'

export class SemanticManagementPage {
  private datasets: Dataset[] = []
  private metrics: Metric[] = []

  constructor(
    private readonly root: HTMLElement,
    private readonly api: SemanticApi,
    private readonly workspaceId: Id,
  ) {}

  async load(): Promise<void> {
    this.renderLoading()
    ;[this.datasets, this.metrics] = await Promise.all([
      this.api.datasets(this.workspaceId),
      this.api.metrics(this.workspaceId),
    ])
    this.render()
  }

  private render(): void {
    this.root.replaceChildren()
    const header = document.createElement('header')
    header.className = 'ds-header'
    header.innerHTML = '<div><h1>数据集与业务指标</h1><p>统一维护字段口径、关联、权限预览和指标版本</p></div>'
    const actions = document.createElement('div')
    actions.className = 'actions'
    actions.append(
      this.button('新建数据集', 'primary', () => this.renderDatasetForm()),
      this.button('新建指标', 'secondary', () => this.renderMetricForm()),
    )
    header.append(actions)
    this.root.append(header, this.datasetSection(), this.metricSection())
  }

  private datasetSection(): HTMLElement {
    const section = document.createElement('section')
    section.innerHTML = '<h2 class="section-title">数据集</h2>'
    if (this.datasets.length === 0) {
      section.append(this.message('还没有数据集。创建草稿并完成字段和关联校验。'))
      return section
    }
    const table = this.table(['名称', '编码', '状态', '当前版本', '操作'])
    const body = table.createTBody()
    this.datasets.forEach(dataset => {
      const row = body.insertRow()
      row.append(this.cell(dataset.name), this.cell(dataset.code), this.statusCell(dataset.status),
        this.cell(dataset.currentVersionId ? String(dataset.currentVersionId) : '未发布'))
      const actions = row.insertCell()
      actions.className = 'actions'
      actions.append(
        this.button('校验', 'ghost', () => void this.validateDataset(dataset)),
        this.button('发布', 'primary', () => void this.publishDataset(dataset)),
        this.button('权限预览', 'ghost', () => void this.previewDataset(dataset)),
      )
    })
    section.append(table)
    return section
  }

  private metricSection(): HTMLElement {
    const section = document.createElement('section')
    section.innerHTML = '<h2 class="section-title">业务指标</h2>'
    if (this.metrics.length === 0) {
      section.append(this.message('还没有指标。指标只能引用已发布的数据集版本。'))
      return section
    }
    const table = this.table(['名称', '编码', '单位', '状态', '操作'])
    const body = table.createTBody()
    this.metrics.forEach(metric => {
      const row = body.insertRow()
      row.append(this.cell(metric.name), this.cell(metric.code), this.cell(metric.unit),
        this.statusCell(metric.status))
      const actions = row.insertCell()
      actions.className = 'actions'
      actions.append(
        this.button('校验', 'ghost', () => void this.validateMetric(metric)),
        this.button('发布', 'primary', () => void this.publishMetric(metric)),
      )
    })
    section.append(table)
    return section
  }

  private renderDatasetForm(): void {
    this.root.replaceChildren()
    const form = document.createElement('form')
    form.className = 'ds-form-card semantic-form'
    form.innerHTML = `
      <header><div><h1>新建数据集</h1><p>选择字段并定义业务名称、类型、角色与敏感等级</p></div></header>
      <div class="form-grid">
        <label>数据集编码<input name="code" required pattern="[a-z][a-z0-9_]{1,63}" placeholder="sales_orders"></label>
        <label>业务名称<input name="name" required placeholder="销售订单"></label>
        <label>源对象 ID<input name="sourceObjectId" type="number" min="1" required></label>
      </div>
      <div class="semantic-toolbar"><h2>字段映射</h2><button type="button" class="button ghost" data-add-field>添加字段</button></div>
      <div data-fields></div>
      <label class="json-field">关联配置 JSON
        <textarea name="relations" rows="5">[]</textarea>
        <small>关联必须 approved=true；一对一/多对一的右键需唯一或配置去重规则。</small>
      </label>
      <label class="json-field">权限预览样例 JSON
        <textarea name="previewRows" rows="5">[]</textarea>
      </label>
      <footer><button type="button" class="button ghost" data-cancel>取消</button><button class="button primary">保存草稿</button></footer>`
    const fields = form.querySelector<HTMLElement>('[data-fields]')!
    form.querySelector('[data-add-field]')!.addEventListener('click', () => fields.append(this.fieldEditor()))
    form.querySelector('[data-cancel]')!.addEventListener('click', () => this.render())
    fields.append(this.fieldEditor())
    form.addEventListener('submit', event => void this.submitDataset(event, form))
    this.root.append(form)
  }

  private fieldEditor(): HTMLElement {
    const row = document.createElement('div')
    row.className = 'field-editor'
    row.innerHTML = `
      <input data-field="stableFieldKey" required placeholder="稳定字段键">
      <input data-field="sourceColumnId" type="number" min="1" placeholder="源字段 ID">
      <input data-field="physicalName" required placeholder="物理字段名">
      <input data-field="businessName" required placeholder="业务名称">
      <select data-field="dataType"><option>STRING</option><option>INTEGER</option><option>LONG</option><option>DECIMAL</option><option>DATE</option><option>DATETIME</option><option>BOOLEAN</option></select>
      <select data-field="role"><option>DIMENSION</option><option>MEASURE</option></select>
      <select data-field="sensitivity"><option>INTERNAL</option><option>PUBLIC</option><option>CONFIDENTIAL</option><option>RESTRICTED</option></select>
      <button type="button" class="button danger" aria-label="删除字段">删除</button>`
    row.lastElementChild!.addEventListener('click', () => row.remove())
    return row
  }

  private async submitDataset(event: SubmitEvent, form: HTMLFormElement): Promise<void> {
    event.preventDefault()
    const data = new FormData(form)
    const fields: DatasetInput['fields'] = Array.from(form.querySelectorAll<HTMLElement>('.field-editor'))
      .map(row => {
        const value = (name: string) => (row.querySelector<HTMLInputElement | HTMLSelectElement>(
          `[data-field="${name}"]`,
        )?.value ?? '').trim()
        const sourceColumnId = value('sourceColumnId')
        return {
          stableFieldKey: value('stableFieldKey'),
          ...(sourceColumnId ? { sourceColumnId: resourceId(sourceColumnId) } : {}),
          physicalName: value('physicalName'),
          businessName: value('businessName'),
          dataType: value('dataType'),
          role: value('role') as DatasetField['role'],
          sensitivity: value('sensitivity') as DatasetField['sensitivity'],
        }
      })
    const input: DatasetInput = {
      workspaceId: this.workspaceId,
      code: String(data.get('code')),
      name: String(data.get('name')),
      sourceObjectId: resourceId(data.get('sourceObjectId')),
      fields,
      relations: this.json(String(data.get('relations')), '关联配置'),
      previewRows: this.json(String(data.get('previewRows')), '预览样例'),
    }
    await this.api.createDataset(input)
    await this.load()
  }

  private renderMetricForm(): void {
    this.root.replaceChildren()
    const form = document.createElement('form')
    form.className = 'ds-form-card semantic-form'
    form.innerHTML = `
      <header><div><h1>新建业务指标</h1><p>公式仅允许受控 AST，时间区间固定使用 [start, end)</p></div></header>
      <div class="form-grid">
        <label>指标编码<input name="code" required pattern="[a-z][a-z0-9_]{1,63}" placeholder="net_sales"></label>
        <label>指标名称<input name="name" required placeholder="净销售额"></label>
        <label>单位<input name="unit" required value="CNY"></label>
        <label>数据集版本 ID<input name="datasetVersionId" type="number" min="1" required></label>
        <label>金额精度<input name="precisionScale" type="number" min="0" max="8" value="2" required></label>
        <label>允许维度（逗号分隔）<input name="dimensions" placeholder="region_id,business_time"></label>
        <label>依赖指标版本（逗号分隔）<input name="dependencies" placeholder="101,102"></label>
      </div>
      <label class="json-field">公式 AST<textarea name="formula" rows="12">{"op":"DIV","args":[{"op":"SUM","args":[{"op":"CASE","args":[{"op":"EQ","args":[{"op":"FIELD","field":"order_status"},{"op":"LITERAL","value":"PAID"}]},{"op":"SUB","args":[{"op":"FIELD","field":"paid_minor_units"},{"op":"FIELD","field":"refunded_minor_units"}]},{"op":"LITERAL","value":0}]}]},{"op":"LITERAL","value":100}]}</textarea><small>默认净销售额口径：仅 PAID 订单，实付分减已确认退款分，汇总后除以 100 转为元；取消订单不计入。</small></label>
      <label class="json-field">时间规则<textarea name="time" rows="4">{"timezone":"Asia/Shanghai","eventTimeField":"business_time","startInclusive":true,"endExclusive":true}</textarea></label>
      <label class="json-field">币种规则<textarea name="currency" rows="4">{"mode":"SINGLE","currencyField":"currency"}</textarea></label>
      <footer><button type="button" class="button ghost" data-cancel>取消</button><button class="button primary">保存草稿</button></footer>`
    form.querySelector('[data-cancel]')!.addEventListener('click', () => this.render())
    form.addEventListener('submit', event => void this.submitMetric(event, form))
    this.root.append(form)
  }

  private async submitMetric(event: SubmitEvent, form: HTMLFormElement): Promise<void> {
    event.preventDefault()
    const data = new FormData(form)
    const numbers = (value: FormDataEntryValue | null) => String(value ?? '').split(',')
      .map(item => item.trim()).filter(Boolean).map(resourceId)
    const strings = (value: FormDataEntryValue | null) => String(value ?? '').split(',')
      .map(item => item.trim()).filter(Boolean)
    const input: MetricInput = {
      workspaceId: this.workspaceId,
      code: String(data.get('code')),
      name: String(data.get('name')),
      unit: String(data.get('unit')),
      datasetVersionId: resourceId(data.get('datasetVersionId')),
      precisionScale: Number(data.get('precisionScale')),
      allowedDimensions: strings(data.get('dimensions')),
      dependencyVersionIds: numbers(data.get('dependencies')),
      formulaAst: this.json(String(data.get('formula')), '公式 AST'),
      timeSpec: this.json(String(data.get('time')), '时间规则'),
      currencySpec: this.json(String(data.get('currency')), '币种规则'),
    }
    await this.api.createMetric(input)
    await this.load()
  }

  private async validateDataset(dataset: Dataset): Promise<void> {
    this.showValidation(`数据集“${dataset.name}”`, await this.api.validateDataset(dataset.id))
    await this.load()
  }

  private async publishDataset(dataset: Dataset): Promise<void> {
    await this.api.publishDataset(dataset.id)
    await this.load()
  }

  private async validateMetric(metric: Metric): Promise<void> {
    this.showValidation(`指标“${metric.name}”`, await this.api.validateMetric(metric.id))
    await this.load()
  }

  private async publishMetric(metric: Metric): Promise<void> {
    await this.api.publishMetric(metric.id)
    await this.load()
  }

  private async previewDataset(dataset: Dataset): Promise<void> {
    const preview = await this.api.previewDataset(dataset.id)
    this.renderPreview(dataset, preview)
  }

  private renderPreview(dataset: Dataset, preview: PreviewData): void {
    this.root.replaceChildren()
    const card = document.createElement('section')
    card.className = 'ds-form-card preview-card'
    const header = document.createElement('header')
    header.innerHTML = `<div><h1>${escapeHtml(dataset.name)} · 权限预览</h1><p>策略前 ${preview.totalBeforePolicy} 行，当前成员可见 ${preview.visibleAfterPolicy} 行</p></div>`
    header.append(this.button('返回', 'ghost', () => this.render()))
    card.append(header)
    if (preview.rows.length === 0) card.append(this.message('当前成员没有可见样例数据。'))
    else {
      const table = this.table(preview.fields.map(field => field.businessName))
      const body = table.createTBody()
      preview.rows.forEach(value => {
        const row = body.insertRow()
        preview.fields.forEach(field => row.append(this.cell(String(value[field.stableFieldKey] ?? ''))))
      })
      card.append(table)
    }
    this.root.append(card)
  }

  private showValidation(subject: string, result: ValidationResult): void {
    const messages = [...result.errors, ...result.warnings].map(issue => `${issue.path}: ${issue.message}`)
    globalThis.alert(result.valid ? `${subject}校验通过${messages.length ? `\n${messages.join('\n')}` : ''}`
      : `${subject}校验失败\n${messages.join('\n')}`)
  }

  private json<T>(value: string, label: string): T {
    try { return JSON.parse(value) as T } catch { throw new Error(`${label}不是合法 JSON`) }
  }

  private renderLoading(): void { this.root.replaceChildren(this.message('正在加载数据集与指标…')) }
  private message(text: string): HTMLElement {
    const node = document.createElement('div'); node.className = 'ds-empty compact'; node.textContent = text; return node
  }
  private cell(text: string): HTMLTableCellElement {
    const node = document.createElement('td'); node.textContent = text; return node
  }
  private statusCell(status: string): HTMLTableCellElement {
    const node = this.cell({ DRAFT: '草稿', VALIDATED: '已校验', PUBLISHED: '已发布' }[status] ?? status)
    node.className = `semantic-status ${status.toLowerCase()}`; return node
  }
  private table(headers: string[]): HTMLTableElement {
    const table = document.createElement('table'); table.className = 'ds-table'
    const head = table.createTHead().insertRow(); headers.forEach(value => { const cell = document.createElement('th'); cell.textContent = value; head.append(cell) })
    return table
  }
  private button(label: string, style: string, action: () => void): HTMLButtonElement {
    const button = document.createElement('button'); button.type = 'button'; button.className = `button ${style}`
    button.textContent = label; button.addEventListener('click', action); return button
  }
}

function escapeHtml(value: string): string {
  const node = document.createElement('span'); node.textContent = value; return node.innerHTML
}
