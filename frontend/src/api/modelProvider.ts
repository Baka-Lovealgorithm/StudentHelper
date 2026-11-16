import { unwrap } from './client'

/** CR-012：通用厂商配置 = 名称 / Base URL / Key；模型单独挂在厂商下。 */
export interface ProviderModelItem {
  id: number
  providerId: number
  modelName: string
  visionSupported: boolean
  streamSupported: boolean
  extraOptions: string | null
  isDefault: boolean
  /** CR-014：调试 / 桩服务模型，其调用不计入用量看板。 */
  isDebug: boolean
}

export interface ProviderItem {
  id: number
  name: string
  baseUrl: string
  hasApiKey: boolean
  models: ProviderModelItem[]
  updatedAt: string
}

export interface ProviderList {
  providers: ProviderItem[]
  defaultModelId: number | null
}

export interface ProviderOptionModel {
  id: number
  modelName: string
}

export interface ProviderOption {
  providerId: number
  providerName: string
  models: ProviderOptionModel[]
}

export interface ProviderOptions {
  options: ProviderOption[]
  defaultModelId: number | null
}

export interface ProviderInput {
  name: string
  baseUrl: string
  apiKey: string
}

export interface ModelInput {
  modelName?: string
  modelNames?: string[]
  visionSupported?: boolean
  streamSupported?: boolean
  extraOptions?: string
  isDebug?: boolean
}

export interface FetchModelsResult {
  models: string[]
  error: string | null
}

/** CR-020：单个模型的连通性测试结果；测试失败是正常结果，不是错误。 */
export interface ModelTestResult {
  modelId: number
  modelName: string
  ok: boolean
  httpStatus: number | null
  latencyMs: number
  message: string
}

/** CR-020：厂商「全部测试」汇总，results 顺序与模型列表一致。 */
export interface ProviderTestResult {
  providerId: number
  providerName: string
  total: number
  success: number
  failed: number
  elapsedMs: number
  message: string
  results: ModelTestResult[]
}

export interface ModelTemplate {
  provider: string
  label: string
  baseUrl: string
  visionSupported: boolean
  note: string
}

export async function listProviders(): Promise<ProviderList> {
  return unwrap<ProviderList>(await fetch('/api/model-providers'))
}

export async function getProviderOptions(): Promise<ProviderOptions> {
  return unwrap<ProviderOptions>(await fetch('/api/model-providers/options'))
}

/** 模板端点沿用 S1 的 /api/model-configs/templates（只填地址，不含 Key）。 */
export async function listTemplates(): Promise<ModelTemplate[]> {
  return unwrap<ModelTemplate[]>(await fetch('/api/model-configs/templates'))
}

export async function saveProvider(input: ProviderInput, id?: number): Promise<number> {
  const response = await fetch(id ? `/api/model-providers/${id}` : '/api/model-providers', {
    method: id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  const data = await unwrap<{ id: number }>(response)
  return data.id
}

export async function deleteProvider(id: number): Promise<void> {
  await unwrap<unknown>(await fetch(`/api/model-providers/${id}`, { method: 'DELETE' }))
}

export async function fetchRemoteModels(id: number): Promise<FetchModelsResult> {
  return unwrap<FetchModelsResult>(await fetch(`/api/model-providers/${id}/fetch-models`, { method: 'POST' }))
}

export async function addProviderModels(id: number, input: ModelInput): Promise<ProviderModelItem[]> {
  return unwrap<ProviderModelItem[]>(await fetch(`/api/model-providers/${id}/models`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  }))
}

export async function updateProviderModel(
  providerId: number, modelId: number, input: ModelInput,
): Promise<ProviderModelItem> {
  return unwrap<ProviderModelItem>(await fetch(`/api/model-providers/${providerId}/models/${modelId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  }))
}

export async function deleteProviderModel(providerId: number, modelId: number): Promise<void> {
  await unwrap<unknown>(await fetch(`/api/model-providers/${providerId}/models/${modelId}`, {
    method: 'DELETE',
  }))
}

export async function setDefaultModel(providerId: number, modelId: number): Promise<void> {
  await unwrap<unknown>(await fetch(`/api/model-providers/${providerId}/models/${modelId}/default`, {
    method: 'POST',
  }))
}

/** CR-020：单模型连通性测试（后端发真实最小对话请求，不落库、不计入看板）。 */
export async function testProviderModel(providerId: number, modelId: number): Promise<ModelTestResult> {
  return unwrap<ModelTestResult>(await fetch(
    `/api/model-providers/${providerId}/models/${modelId}/test`, { method: 'POST' },
  ))
}

/** CR-020：厂商全部模型连通性测试（该厂商模型列表下的全部模型）。 */
export async function testProvider(providerId: number): Promise<ProviderTestResult> {
  return unwrap<ProviderTestResult>(await fetch(`/api/model-providers/${providerId}/test`, {
    method: 'POST',
  }))
}
