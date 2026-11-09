import { unwrap } from './client'

export interface McpServerItem {
  id: number
  name: string
  transport: string
  url: string
  enabled: boolean
  lastStatus: string | null
  lastError: string | null
  toolCount: number
  updatedAt: string
}

export interface McpTestResult {
  id: number
  status: string
  toolCount: number
  tools: string[]
  error: string | null
}

export async function listMcpServers(): Promise<McpServerItem[]> {
  return unwrap<McpServerItem[]>(await fetch('/api/mcp-servers'))
}

export async function createMcpServer(payload: {
  name: string
  transport: string
  url: string
  enabled: boolean
}): Promise<McpServerItem> {
  return unwrap<McpServerItem>(await fetch('/api/mcp-servers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }))
}

export async function updateMcpServer(id: number, payload: {
  name: string
  transport: string
  url: string
  enabled: boolean
}): Promise<McpServerItem> {
  return unwrap<McpServerItem>(await fetch(`/api/mcp-servers/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }))
}

export async function testMcpServer(id: number): Promise<McpTestResult> {
  return unwrap<McpTestResult>(await fetch(`/api/mcp-servers/${id}/test`, { method: 'POST' }))
}

export async function deleteMcpServer(id: number): Promise<{ deleted: boolean }> {
  return unwrap<{ deleted: boolean }>(await fetch(`/api/mcp-servers/${id}`, { method: 'DELETE' }))
}
