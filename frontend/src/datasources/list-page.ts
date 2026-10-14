import type { Id } from '../api/resource-id'
import { DatasourceApi } from './api'
import { connectionFailure, connectionSuccess } from './connection-status'
import type { ConnectionTestState, Datasource } from './types'

export class DatasourceListPage {
  private datasources: Datasource[] = []
  private busy = false

  constructor(
    private readonly root: HTMLElement,
    private readonly api: DatasourceApi,
    private readonly onCreate: () => void,
    private readonly onEdit: (datasource: Datasource) => void,
  ) {}

  async load(workspaceId?: Id): Promise<void> {
    this.busy = true
    this.render()
    try {
      this.datasources = await this.api.list(workspaceId)
    } finally {
      this.busy = false
      this.render()
    }
  }

  private render(): void {
    this.root.replaceChildren()
    const header = document.createElement('header')
    header.className = 'ds-header'
    header.innerHTML = '<div><h1>数据源</h1><p>管理只读数据库连接与元数据采集</p></div>'
    const create = this.button('新增数据源', 'primary', this.onCreate)
    header.append(create)
    this.root.append(header)
    if (this.busy) {
      this.root.append(this.message('正在加载数据源…'))
      return
    }
    if (this.datasources.length === 0) {
      this.root.append(this.message('还没有数据源。新增一个 MySQL 或 PostgreSQL 只读连接。'))
      return
    }
    const table = document.createElement('table')
    table.className = 'ds-table'
    table.innerHTML = '<thead><tr><th>名称</th><th>类型</th><th>地址</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead>'
    const body = document.createElement('tbody')
    this.datasources.forEach((datasource) => body.append(this.row(datasource)))
    table.append(body)
    this.root.append(table)
  }

  private row(datasource: Datasource): HTMLTableRowElement {
    const row = document.createElement('tr')
    const name = document.createElement('td')
    name.innerHTML = `<strong>${escapeHtml(datasource.name)}</strong><small>${escapeHtml(datasource.code)}</small>`
    const type = this.cell(datasource.type === 'MYSQL' ? 'MySQL' : 'PostgreSQL')
    const endpoint = this.cell(`${datasource.endpoint.host}:${datasource.endpoint.port}/${datasource.endpoint.database}`)
    const status = this.cell(datasource.status === 'ACTIVE' ? '已启用' : '已停用')
    status.className = `status ${datasource.status.toLowerCase()}`
    const updated = this.cell(new Date(datasource.updatedAt).toLocaleString('zh-CN'))
    const actions = document.createElement('td')
    actions.className = 'actions'
    actions.append(this.button('编辑', 'ghost', () => this.onEdit(datasource)))
    actions.append(this.button('测试连接', 'ghost', () => void this.test(datasource, row)))
    if (datasource.status === 'ACTIVE') {
      actions.append(this.button('停用', 'danger', () => void this.deactivate(datasource)))
    }
    row.append(name, type, endpoint, status, updated, actions)
    return row
  }

  private async test(datasource: Datasource, row: HTMLElement): Promise<void> {
    this.showTestState(row, { kind: 'testing', message: '正在测试连接…' })
    try {
      this.showTestState(row, connectionSuccess(await this.api.testSaved(datasource.id)))
    } catch (error) {
      this.showTestState(row, connectionFailure(error))
    }
  }

  private async deactivate(datasource: Datasource): Promise<void> {
    if (!globalThis.confirm(`确认停用“${datasource.name}”？现有凭据将立即失效。`)) return
    const updated = await this.api.deactivate(datasource)
    this.datasources = this.datasources.map((value) => value.id === updated.id ? updated : value)
    this.render()
  }

  private showTestState(row: HTMLElement, state: ConnectionTestState): void {
    let message = row.querySelector<HTMLElement>('.connection-state')
    if (!message) {
      message = document.createElement('div')
      message.className = 'connection-state'
      row.lastElementChild?.append(message)
    }
    message.dataset.kind = state.kind
    message.textContent = state.kind === 'success' ? `${state.message}（${state.latencyMs}ms）` : state.message
  }

  private cell(value: string): HTMLTableCellElement {
    const cell = document.createElement('td')
    cell.textContent = value
    return cell
  }

  private message(value: string): HTMLElement {
    const message = document.createElement('div')
    message.className = 'ds-empty'
    message.textContent = value
    return message
  }

  private button(label: string, style: string, onClick: () => void): HTMLButtonElement {
    const button = document.createElement('button')
    button.type = 'button'
    button.className = `button ${style}`
    button.textContent = label
    button.addEventListener('click', onClick)
    return button
  }
}

function escapeHtml(value: string): string {
  const node = document.createElement('span')
  node.textContent = value
  return node.innerHTML
}
