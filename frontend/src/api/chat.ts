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

  const response = await fetch('/api/chat/stream', { method: 'POST', body: form })
  await consumeSse(response, onEvent)
}
