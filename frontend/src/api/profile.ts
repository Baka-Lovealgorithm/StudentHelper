import { unwrap } from './client'

export interface ProfileResponse {
  profileJson: string | null
}

export interface MemoryItem {
  id: number
  kind: string
  content: string
  sourceSessionId: number | null
  enabled: boolean
  createdAt: string
}

export async function getProfile(): Promise<ProfileResponse> {
  return unwrap<ProfileResponse>(await fetch('/api/profile'))
}

export async function updateProfile(patch: Record<string, unknown>): Promise<ProfileResponse> {
  return unwrap<ProfileResponse>(await fetch('/api/profile', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(patch),
  }))
}

export async function clearProfile(): Promise<boolean> {
  return unwrap<boolean>(await fetch('/api/profile', { method: 'DELETE' }))
}

export async function listMemories(): Promise<MemoryItem[]> {
  return unwrap<MemoryItem[]>(await fetch('/api/memories'))
}

export async function addMemory(kind: string, content: string, sourceSessionId?: number | null): Promise<MemoryItem> {
  return unwrap<MemoryItem>(await fetch('/api/memories', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ kind, content, sourceSessionId: sourceSessionId ?? null }),
  }))
}

export async function disableMemory(id: number): Promise<boolean> {
  return unwrap<boolean>(await fetch(`/api/memories/${id}`, { method: 'DELETE' }))
}
