import { consumeSse, type SseEvent } from './sse'

export interface SubmitOptions {
  sessionId: number
  text: string
  hint?: string
  guideMode: number
  images: File[]
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

  const response = await fetch('/api/chat/stream', { method: 'POST', body: form })
  await consumeSse(response, onEvent)
}
