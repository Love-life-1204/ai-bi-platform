import type { Id } from '../api/resource-id'
import type { ApiClient } from '../api/client'
import type { AiAnswer, AiConversation, AiEvidence, AiMessage, AiTask, AiTaskAccepted } from './types'

export class AiApi {
  constructor(private readonly client: ApiClient) {}

  listConversations(): Promise<AiConversation[]> {
    return this.client.request('ai/conversations')
  }

  createConversation(workspaceId: Id, title: string): Promise<AiConversation> {
    return this.client.request('ai/conversations', { method: 'POST', body: { workspaceId, title } })
  }

  getConversation(id: Id): Promise<AiConversation> {
    return this.client.request(`ai/conversations/${id}`)
  }

  messages(id: Id): Promise<AiMessage[]> {
    return this.client.request(`ai/conversations/${id}/messages`)
  }

  ask(id: Id, question: string, contextVersion: number, datasetId?: Id, requestKey: string = crypto.randomUUID()): Promise<AiTaskAccepted> {
    return this.client.request(`ai/conversations/${id}/messages`, {
      method: 'POST',
      body: { question, confirmedContextVersion: contextVersion, ...(datasetId ? { datasetId } : {}) },
      idempotencyKey: requestKey, timeoutMs: 20_000,
    })
  }

  task(taskId: string): Promise<AiTask> {
    return this.client.request(`ai/tasks/${taskId}`)
  }

  resume(taskId: string, clarification: Record<string, string | number | boolean>, contextVersion: number): Promise<AiTaskAccepted> {
    return this.client.request(`ai/tasks/${taskId}/resume`, {
      method: 'POST',
      body: { clarification, confirmedContextVersion: contextVersion },
      idempotencyKey: crypto.randomUUID(), timeoutMs: 20_000,
    })
  }

  cancel(taskId: string): Promise<AiTask> {
    return this.client.request(`ai/tasks/${taskId}/cancel`, { method: 'POST' })
  }

  answer(taskId: string): Promise<AiAnswer> {
    return this.client.request(`ai/tasks/${taskId}/answer`)
  }

  evidence(taskId: string): Promise<AiEvidence[]> {
    return this.client.request(`ai/tasks/${taskId}/evidence`)
  }

  feedback(taskId: string, rating: 0 | 1, comment?: string): Promise<void> {
    return this.client.request(`ai/tasks/${taskId}/feedback`, {
      method: 'POST', body: { rating, ...(comment ? { comment } : {}) }, silent: true,
    })
  }

  stream(taskId: string, signal: AbortSignal, lastEventId?: string): Promise<Response> {
    return this.client.requestStream(`ai/tasks/${taskId}/events`, signal, lastEventId)
  }
}

