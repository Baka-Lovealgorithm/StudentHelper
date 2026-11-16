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

/**
 * 查询串构造（CR-043）：空串 / null / undefined 一律跳过，其余按 String 输出。
 * 改造前 knowledge / wrongBook / session 各写一套，其中 session 还是手拼模板串。
 */
export function buildQuery(
  params: Record<string, string | number | boolean | undefined | null>,
): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    search.set(key, String(value))
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}

/** GET + 拆包；params 省略时不带查询串。 */
export async function get<T>(
  path: string,
  params?: Record<string, string | number | boolean | undefined | null>,
): Promise<T> {
  return unwrap<T>(await fetch(params ? path + buildQuery(params) : path))
}

/** POST JSON + 拆包；body 为 undefined 时发送空对象，与既有接口约定一致。 */
export async function post<T>(path: string, body: unknown = {}): Promise<T> {
  return unwrap<T>(await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }))
}

/** DELETE + 拆包（忽略返回体）。 */
export async function del(path: string): Promise<void> {
  await unwrap<unknown>(await fetch(path, { method: 'DELETE' }))
}

/**
 * 健康检查：走统一拆包，因此「HTTP 200 但业务码非 OK」也会被判为不可用。
 * 改造前只看 `response.ok`，后端异常兜底成 200 + E5000 时顶栏仍显示「已连接」。
 */
export async function fetchHealth(): Promise<HealthData> {
  return unwrap<HealthData>(await fetch('/api/health'))
}
