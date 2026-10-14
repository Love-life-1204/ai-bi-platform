import { optionalResourceId, resourceId } from '../api/resource-id'
import type { Id } from '../api/resource-id'
import { QueryApi } from './api'
import type { QueryFilter, QueryPlan, QueryResult } from './types'

export class QueryPage {
  constructor(private readonly root: HTMLElement, private readonly api: QueryApi) {}

  render(): void {
    this.root.replaceChildren()
    const form = document.createElement('form')
    form.className = 'ds-form-card semantic-form'
    form.innerHTML = `
      <header><div><h1>统一数据查询</h1><p>查询只使用已发布的数据集和指标版本，权限与数据版本由服务端固定。</p></div></header>
      <div class="form-grid">
        <label>数据集版本 ID<input name="datasetVersionId" type="number" min="1" required></label>
        <label>指标版本 ID（逗号分隔）<input name="metricVersionIds" required placeholder="701,702"></label>
        <label>维度字段 ID（逗号分隔）<input name="dimensionFieldIds" placeholder="101,102"></label>
        <label>返回行数<input name="limit" type="number" min="1" max="10000" value="100" required></label>
        <label>排序类型<select name="sortType"><option value="">不排序</option><option>METRIC</option><option>FIELD</option></select></label>
        <label>排序目标 ID<input name="sortTargetId" type="number" min="1"></label>
      </div>
      <label class="json-field">筛选 JSON
        <textarea name="filters" rows="5">[]</textarea>
        <small>仅接受字段 ID、白名单操作符和值；不接受 SQL。</small>
      </label>
      <footer><button class="button primary">运行查询</button></footer>`
    form.addEventListener('submit', event => void this.submit(event, form))
    this.root.append(form)
  }

  private async submit(event: SubmitEvent, form: HTMLFormElement): Promise<void> {
    event.preventDefault()
    const data = new FormData(form)
    const numbers = (name: string) => String(data.get(name) ?? '').split(',')
      .map(value => value.trim()).filter(Boolean).map(resourceId)
    const sortType = String(data.get('sortType')) as '' | 'FIELD' | 'METRIC'
    const sortTargetId = optionalResourceId(data.get('sortTargetId'))
    let filters: QueryFilter[]
    try {
      filters = JSON.parse(String(data.get('filters'))) as QueryFilter[]
    } catch {
      globalThis.alert('筛选 JSON 格式不正确')
      return
    }
    const plan: QueryPlan = {
      datasetVersionId: resourceId(data.get('datasetVersionId')),
      metricVersionIds: numbers('metricVersionIds'),
      dimensionFieldIds: numbers('dimensionFieldIds'),
      filters,
      sort: sortType && sortTargetId
        ? [{ targetType: sortType, targetId: sortTargetId, direction: 'DESC' }]
        : [],
      limit: Number(data.get('limit')),
    }
    const submit = form.querySelector<HTMLButtonElement>('button[type="submit"], footer button')!
    submit.disabled = true
    submit.textContent = '查询中…'
    try {
      this.renderResult(await this.api.execute(plan))
    } finally {
      submit.disabled = false
      submit.textContent = '运行查询'
    }
  }

  private renderResult(result: QueryResult): void {
    this.root.replaceChildren()
    const section = document.createElement('section')
    section.className = 'ds-form-card preview-card'
    const header = document.createElement('header')
    header.innerHTML = `<div><h1>查询结果</h1><p>查询号 ${escapeHtml(result.queryId)} · ${result.rowCount} 行 · ${result.executionMs} ms${result.cached ? ' · 缓存命中' : ''}</p><small>数据版本 generation=${result.publication.generationId}, publish_seq=${result.publication.committedPublishSeq}</small></div>`
    const back = document.createElement('button')
    back.type = 'button'; back.className = 'button ghost'; back.textContent = '返回查询'
    back.addEventListener('click', () => this.render())
    header.append(back)
    section.append(header)
    if (result.rows.length === 0) {
      const empty = document.createElement('div'); empty.className = 'ds-empty compact'; empty.textContent = '没有符合条件的数据。'
      section.append(empty)
    } else {
      const table = document.createElement('table'); table.className = 'ds-table'
      const head = table.createTHead().insertRow()
      result.columns.forEach(column => { const cell = document.createElement('th'); cell.textContent = column.label; head.append(cell) })
      const body = table.createTBody()
      result.rows.forEach(value => {
        const row = body.insertRow()
        result.columns.forEach(column => { const cell = row.insertCell(); cell.textContent = String(value[column.key] ?? '') })
      })
      section.append(table)
    }
    this.root.append(section)
  }
}

function escapeHtml(value: string): string {
  const node = document.createElement('span'); node.textContent = value; return node.innerHTML
}
