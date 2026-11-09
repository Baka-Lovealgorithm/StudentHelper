import { unwrap } from './client'

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
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  const suffix = params.toString()
  return unwrap<WrongBookItem[]>(await fetch(`/api/wrong-book${suffix ? `?${suffix}` : ''}`))
}
