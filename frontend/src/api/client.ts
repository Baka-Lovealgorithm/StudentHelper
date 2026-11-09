export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export interface HealthData {
  status: string
  app: string
  time: string
}

export async function fetchHealth(): Promise<ApiResponse<HealthData>> {
  const response = await fetch('/api/health')
  if (!response.ok) throw new Error(`健康检查失败：HTTP ${response.status}`)
  return (await response.json()) as ApiResponse<HealthData>
}

/** 统一拆包：非 2xx 或业务码非 OK 都转成 Error，供各 api 模块复用。 */
export async function unwrap<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiResponse<unknown> | null
    throw new Error(body?.message ?? `请求失败：HTTP ${response.status}`)
  }
  const body = (await response.json()) as ApiResponse<T>
  if (body.code !== 'OK') throw new Error(body.message)
  return body.data
}
