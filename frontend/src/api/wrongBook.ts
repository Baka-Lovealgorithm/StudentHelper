import { del, get, unwrap } from './client'

export interface WrongBookItem {
  feedbackId: number
  messageId: number
  sessionId: number
  question: string
  subject: string | null
  note: string | null
  createdAt: string
}

export interface WrongBookQuery {
  subject?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export async function markWrong(messageId: number, subject?: string, note?: string) {
  return unwrap<{ id: number; messageId: number }>(await fetch('/api/wrong-book', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messageId, subject: subject ?? '数据结构', note: note ?? null }),
  }))
}

export async function listWrongBook(query: WrongBookQuery = {}): Promise<WrongBookItem[]> {
  return get<WrongBookItem[]>('/api/wrong-book', { ...query })
}

/** CR-022：单条错题删除——只删标记行，消息、会话与图片不受影响。 */
export async function deleteWrongBook(feedbackId: number): Promise<void> {
  await del(`/api/wrong-book/${feedbackId}`)
}
