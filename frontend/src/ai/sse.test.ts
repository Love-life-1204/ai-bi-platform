import { describe, expect, it, vi } from 'vitest'
import { AiEventStream } from './sse'

describe('AiEventStream', () => {
  it('sends Last-Event-ID and stops after done', async () => {
    const values = new Map<string, string>()
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key),
    })
    sessionStorage.setItem('aibi.ai.lastEvent.42', '42:2')
    const chunks = [
      'id: 42:3\nevent: progress\ndata: {"stage":"query"}\n\n',
      'id: 42:4\nevent: done\ndata: {"status":"SUCCEEDED"}\n\n',
    ]
    const api = {
      stream: vi.fn(async (_task: string, _signal: AbortSignal, last?: string) => {
        expect(last).toBe('42:2')
        return new Response(new ReadableStream({
          start(controller) { chunks.forEach(value => controller.enqueue(new TextEncoder().encode(value))); controller.close() },
        }))
      }),
    }
    const received: string[] = []
    await new AiEventStream(api as never, '42').connect(new AbortController().signal, {
      onEvent: event => received.push(event.type),
    })
    expect(received).toEqual(['progress', 'done'])
    expect(sessionStorage.getItem('aibi.ai.lastEvent.42')).toBe('42:4')
  })
})

