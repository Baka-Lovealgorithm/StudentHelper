import { unwrap } from './client'

export interface SubjectItem {
  id: number
  name: string
  promptHint: string | null
  current: boolean
  createdAt: string
}

export async function listSubjects(): Promise<SubjectItem[]> {
  return unwrap<SubjectItem[]>(await fetch('/api/subjects'))
}

export async function createSubject(name: string, promptHint: string): Promise<SubjectItem> {
  return unwrap<SubjectItem>(await fetch('/api/subjects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, promptHint }),
  }))
}

export async function activateSubject(id: number): Promise<SubjectItem> {
  return unwrap<SubjectItem>(await fetch(`/api/subjects/${id}/activate`, { method: 'POST' }))
}

export async function deleteSubject(id: number): Promise<{ deleted: boolean }> {
  return unwrap<{ deleted: boolean }>(await fetch(`/api/subjects/${id}`, { method: 'DELETE' }))
}
