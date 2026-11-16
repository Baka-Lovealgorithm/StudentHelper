import { consumeSse, type SseEvent } from './sse'

/** 有序图文段，与后端 ContentPart 对应。 */
export interface ContentPartRef {
  type: 'text' | 'image'
  value?: string
  index?: number
}

export interface SubmitOptions {
  sessionId: number
  text: string
  hint?: string
  guideMode: number
  images: File[]
  parts?: ContentPartRef[]
  /** CR-066：举一反三出题轮标记——后端跳过意图分类、强制 FOLLOW_UP 并追加「只出题」约束。 */
  practice?: boolean
}

/** 提交题目并消费 SSE：status / delta / done / error（TD-13）。 */
export async function submitQuestion(
  options: SubmitOptions,
  onEvent: (event: SseEvent) => void,
): Promise<void> {
  const form = new FormData()
  form.append('sessionId', String(options.sessionId))
  form.append('text', options.text)
  form.append('guideMode', String(options.guideMode))
  if (options.hint?.trim()) form.append('hint', options.hint.trim())
  for (const file of options.images) form.append('images', file)
  if (options.parts && options.parts.length > 0) form.append('parts', JSON.stringify(options.parts))
  if (options.practice) form.append('practice', 'true')

  const response = await fetch('/api/chat/stream', { method: 'POST', body: form })
  await consumeSse(response, onEvent)
}

/** CR-053：停止指定会话当前正在生成的回答；true = 已触发后端取消。 */
export async function cancelGeneration(sessionId: number): Promise<boolean> {
  const response = await fetch(`/api/chat/cancel?sessionId=${encodeURIComponent(sessionId)}`, {
    method: 'POST',
  })
  if (!response.ok) throw new Error(`停止失败：HTTP ${response.status}`)
  const body = await response.json() as { code?: string; data?: boolean }
  return body.data === true
}
