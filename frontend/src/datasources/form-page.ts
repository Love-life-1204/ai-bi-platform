import type { Id } from '../api/resource-id'
import { DatasourceApi } from './api'
import { connectionFailure, connectionSuccess } from './connection-status'
import type { ConnectionTestState, Datasource, DatasourceInput, DatasourceType } from './types'

export class DatasourceFormPage {
  private testState: ConnectionTestState = { kind: 'idle', message: '保存前请先测试连接' }

  constructor(
    private readonly root: HTMLElement,
    private readonly api: DatasourceApi,
    private readonly workspaceId: Id,
    private readonly existing: Datasource | undefined,
    private readonly onSaved: (datasource: Datasource) => void,
    private readonly onCancel: () => void,
  ) {}

  render(): void {
    const current = this.existing
    this.root.innerHTML = `
      <section class="ds-form-card">
        <header><h1>${current ? '编辑数据源' : '新增数据源'}</h1><p>仅支持只读 MySQL 与 PostgreSQL 账号</p></header>
        <form novalidate>
          <div class="form-grid">
            <label>名称<input name="name" maxlength="200" required value="${escapeAttribute(current?.name ?? '')}"></label>
            <label>编码<input name="code" pattern="[a-z][a-z0-9_]*" required ${current ? 'disabled' : ''} value="${escapeAttribute(current?.code ?? '')}"></label>
            <label>类型<select name="type" ${current ? 'disabled' : ''}><option value="MYSQL">MySQL</option><option value="POSTGRESQL">PostgreSQL</option></select></label>
            <label>主机<input name="host" required value="${escapeAttribute(current?.endpoint.host ?? '')}" placeholder="db.example.com"></label>
            <label>端口<input name="port" type="number" min="1" max="65535" required value="${current?.endpoint.port ?? 3306}"></label>
            <label>数据库<input name="database" required value="${escapeAttribute(current?.endpoint.database ?? '')}"></label>
            <label>用户名<input name="username" ${current ? '' : 'required'} autocomplete="off"></label>
            <label>密码<input name="password" type="password" ${current ? '' : 'required'} autocomplete="new-password"></label>
            <label class="checkbox"><input name="tls" type="checkbox" ${current?.endpoint.tls === false ? '' : 'checked'}>启用 TLS</label>
          </div>
          <p class="security-note">密码会进入服务端凭据保险库，数据源记录仅保存密钥引用。内网与保留地址默认拒绝。</p>
          <div class="connection-result" data-kind="${this.testState.kind}">${escapeHtml(this.testState.message)}</div>
          <footer><button type="button" class="button ghost" data-action="cancel">取消</button><button type="button" class="button secondary" data-action="test">测试连接</button><button class="button primary">保存</button></footer>
        </form>
      </section>`
    const form = this.root.querySelector<HTMLFormElement>('form')
    if (!form) return
    const type = form.elements.namedItem('type') as HTMLSelectElement
    if (current) type.value = current.type
    type.addEventListener('change', () => {
      const port = form.elements.namedItem('port') as HTMLInputElement
      port.value = type.value === 'MYSQL' ? '3306' : '5432'
    })
    form.querySelector('[data-action="cancel"]')?.addEventListener('click', this.onCancel)
    form.querySelector('[data-action="test"]')?.addEventListener('click', () => void this.test(form))
    form.addEventListener('submit', (event) => {
      event.preventDefault()
      void this.save(form)
    })
  }

  private async test(form: HTMLFormElement): Promise<void> {
    if (!form.reportValidity()) return
    const input = this.read(form)
    this.setTestState({ kind: 'testing', message: '正在测试 DNS、网络、认证与只读权限…' })
    try {
      const usesSavedCredential = this.existing
        && input.credential.username.length === 0
        && input.credential.password.length === 0
      const result = usesSavedCredential
        ? await this.api.testSaved(this.existing.id)
        : await this.api.testDraft(input)
      this.setTestState(connectionSuccess(result))
    } catch (error) {
      this.setTestState(connectionFailure(error))
    }
  }

  private async save(form: HTMLFormElement): Promise<void> {
    if (!form.reportValidity()) return
    const input = this.read(form)
    const credentialPresent = input.credential.username.length > 0 || input.credential.password.length > 0
    if (credentialPresent && (!input.credential.username || !input.credential.password)) {
      this.setTestState({ kind: 'auth-failed', message: '更新凭据时必须同时填写用户名和密码' })
      return
    }
    const saved = this.existing
      ? await this.api.update(this.existing.id, {
          name: input.name,
          endpoint: input.endpoint,
          ...(credentialPresent ? { credential: input.credential } : {}),
          lockVersion: this.existing.lockVersion,
        })
      : await this.api.create(input)
    this.onSaved(saved)
  }

  private read(form: HTMLFormElement): DatasourceInput {
    const data = new FormData(form)
    return {
      workspaceId: this.workspaceId,
      code: String(data.get('code') ?? this.existing?.code ?? '').trim(),
      name: String(data.get('name') ?? '').trim(),
      type: String(data.get('type') ?? this.existing?.type ?? 'MYSQL') as DatasourceType,
      endpoint: {
        host: String(data.get('host') ?? '').trim(),
        port: Number(data.get('port')),
        database: String(data.get('database') ?? '').trim(),
        tls: data.get('tls') === 'on',
      },
      credential: {
        username: String(data.get('username') ?? ''),
        password: String(data.get('password') ?? ''),
      },
    }
  }

  private setTestState(state: ConnectionTestState): void {
    this.testState = state
    const result = this.root.querySelector<HTMLElement>('.connection-result')
    if (!result) return
    result.dataset.kind = state.kind
    result.textContent = state.kind === 'success' ? `${state.message}（${state.latencyMs}ms）` : state.message
  }
}

function escapeAttribute(value: string): string {
  return escapeHtml(value).replaceAll('"', '&quot;')
}

function escapeHtml(value: string): string {
  const node = document.createElement('span')
  node.textContent = value
  return node.innerHTML
}
