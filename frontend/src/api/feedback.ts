import { unwrap } from './client'

/** S2 标记类型：WRONG 供错题本（T-202），INCORRECT / REGENERATE 供反馈闭环（T-207）。 */
export type FeedbackKind = 'WRONG' | 'INCORRECT' | 'REGENERATE'

export interface FeedbackItem {
  id: number
  messageId: number
  sessionId: number
  kind: FeedbackKind
  subject: string | null
  note: string | null
  createdAt: string
}

export async function markFeedback(
  messageId: number,
  kind: FeedbackKind,
  subject?: string,
  note?: string,
): Promise<FeedbackItem> {
  return unwrap<FeedbackItem>(await fetch('/api/feedback', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messageId, kind, subject, note }),
  }))
}

export async function listFeedback(sessionId?: number, kind?: FeedbackKind): Promise<FeedbackItem[]> {
  const params = new URLSearchParams()
  if (sessionId !== undefined) params.set('sessionId', String(sessionId))
  if (kind) params.set('kind', kind)
  const query = params.toString()
  return unwrap<FeedbackItem[]>(await fetch(`/api/feedback${query ? `?${query}` : ''}`))
}
