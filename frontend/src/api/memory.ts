import { unwrap } from './client'

export interface MemoryFileInfo {
  name: string
  size: number
  updatedAt: string
}

export interface MemoryOverview {
  user: string
  model: string
  version: string
  injectMaxChars: number
  files: MemoryFileInfo[]
}

export interface ConsolidationResult {
  status: string
  skipped: boolean
  detail: string
}

export async function getMemory(): Promise<MemoryOverview> {
  return unwrap<MemoryOverview>(await fetch('/api/memory'))
}

export async function getMemoryFile(name: string): Promise<string> {
  return unwrap<string>(await fetch(`/api/memory/file?name=${encodeURIComponent(name)}`))
}

export async function updateMemoryUser(content: string, version: string): Promise<MemoryOverview> {
  return unwrap<MemoryOverview>(await fetch('/api/memory/user', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, version }),
  }))
}

export async function consolidateMemory(): Promise<ConsolidationResult> {
  return unwrap<ConsolidationResult>(await fetch('/api/memory/consolidate', { method: 'POST' }))
}

export async function clearMemory(): Promise<boolean> {
  return unwrap<boolean>(await fetch('/api/memory', { method: 'DELETE' }))
}
