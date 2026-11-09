import type { ApiResponse } from './client'

export interface ModelConfigItem {
  id: number
  name: string
  provider: string
  baseUrl: string
  modelName: string
  visionSupported: boolean
  streamSupported: boolean
  extraOptions: string | null
  isCurrent: boolean
  hasApiKey: boolean
  createdAt: string
  updatedAt: string
}

export interface ModelTemplate {
  provider: string
  label: string
  baseUrl: string
  visionSupported: boolean
  note: string
}

export interface ModelConfigInput {
  name: string
  provider: string
  baseUrl: string
  apiKey: string
  modelName: string
  visionSupported: boolean
  streamSupported: boolean
  extraOptions: string
}

async function unwrap<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiResponse<unknown> | null
    throw new Error(body?.message ?? `请求失败：HTTP ${response.status}`)
  }
  const body = (await response.json()) as ApiResponse<T>
  if (body.code !== 'OK') throw new Error(body.message)
  return body.data
}

export async function listModelConfigs(): Promise<ModelConfigItem[]> {
  return unwrap<ModelConfigItem[]>(await fetch('/api/model-configs'))
}

export async function listTemplates(): Promise<ModelTemplate[]> {
  return unwrap<ModelTemplate[]>(await fetch('/api/model-configs/templates'))
}

export async function saveModelConfig(input: ModelConfigInput, id?: number): Promise<number> {
  const response = await fetch(id ? `/api/model-configs/${id}` : '/api/model-configs', {
    method: id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  const data = await unwrap<{ id: number }>(response)
  return data.id
}

export async function activateModelConfig(id: number): Promise<void> {
  await unwrap<unknown>(await fetch(`/api/model-configs/${id}/activate`, { method: 'POST' }))
}

export async function deleteModelConfig(id: number): Promise<void> {
  await unwrap<unknown>(await fetch(`/api/model-configs/${id}`, { method: 'DELETE' }))
}
