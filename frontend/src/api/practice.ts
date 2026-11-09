import { unwrap } from './client'

export interface Practice {
  id: number
  sourceMessageId: number
  sessionId: number
  kpId: number | null
  variantText: string
  userAnswer: string | null
  judgeResult: string
  score: number | null
  feedback: string | null
  createdAt: string
  judgedAt: string | null
}

export async function generatePractice(sourceMessageId: number, kpId: number | null = null): Promise<Practice> {
  return unwrap<Practice>(await fetch('/api/practice/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sourceMessageId, kpId }),
  }))
}

export async function submitAndJudge(id: number, userAnswer: string): Promise<Practice> {
  await unwrap<Practice>(await fetch(`/api/practice/${id}/answer`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userAnswer }),
  }))
  return unwrap<Practice>(await fetch(`/api/practice/${id}/judge`, { method: 'POST' }))
}
