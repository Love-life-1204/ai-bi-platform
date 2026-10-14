import type { AiApi } from './api'
import type { AiTaskEvent } from './types'

export interface AiStreamHandlers {
  onEvent: (event: AiTaskEvent) => void
  onReconnect?: (attempt: number) => void
}

export class AiEventStream {
  private lastEventId: string | undefined

  constructor(private readonly api: AiApi, private readonly taskId: string) {
    this.lastEventId = sessionStorage.getItem(this.storageKey()) ?? undefined
  }

  async connect(signal: AbortSignal, handlers: AiStreamHandlers): Promise<void> {
    let attempt = 0
    while (!signal.aborted) {
      try {
        const response = await this.api.stream(this.taskId, signal, this.lastEventId)
        if (!response.body) throw new Error('事件流响应没有可读内容')
        if (await this.consume(response.body, signal, handlers.onEvent)) return
      } catch {
        if (signal.aborted) return
      }
      attempt += 1
      handlers.onReconnect?.(attempt)
      await this.delay(Math.min(500 * 2 ** Math.min(attempt, 4), 8_000), signal)
    }
  }

  private async consume(
    stream: ReadableStream<Uint8Array>, signal: AbortSignal,
    onEvent: (event: AiTaskEvent) => void,
  ): Promise<boolean> {
    const reader = stream.getReader(); const decoder = new TextDecoder(); let buffer = ''
    try {
      while (!signal.aborted) {
        const { done, value } = await reader.read()
        buffer += decoder.decode(value, { stream: !done }).replaceAll('\r\n', '\n')
        let boundary = buffer.indexOf('\n\n')
        while (boundary >= 0) {
          const parsed = this.parse(buffer.slice(0, boundary)); buffer = buffer.slice(boundary + 2)
          if (parsed) {
            this.lastEventId = parsed.id || this.lastEventId
            if (this.lastEventId) sessionStorage.setItem(this.storageKey(), this.lastEventId)
            onEvent(parsed); if (parsed.type === 'done') return true
          }
          boundary = buffer.indexOf('\n\n')
        }
        if (done) return false
      }
      return false
    } finally { reader.releaseLock() }
  }

  private parse(block: string): AiTaskEvent | undefined {
    if (!block.trim() || block.startsWith(':')) return undefined
    let id = ''; let type = 'message'; const data: string[] = []
    block.split('\n').forEach(line => {
      const separator = line.indexOf(':'); const field = separator < 0 ? line : line.slice(0, separator)
      const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '')
      if (field === 'id') id = value
      else if (field === 'event') type = value
      else if (field === 'data') data.push(value)
    })
    if (!data.length) return undefined
    return { id, type: type as AiTaskEvent['type'], data: JSON.parse(data.join('\n')) as Record<string, unknown> }
  }

  private storageKey(): string { return `aibi.ai.lastEvent.${this.taskId}` }

  private delay(milliseconds: number, signal: AbortSignal): Promise<void> {
    return new Promise(resolve => {
      const timeout = setTimeout(resolve, milliseconds)
      signal.addEventListener('abort', () => { clearTimeout(timeout); resolve() }, { once: true })
    })
  }
}

