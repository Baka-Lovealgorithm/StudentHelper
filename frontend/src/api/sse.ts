/**
 * SSE 解析器骨架（T-005 / TD-13）。
 * 使用 fetch + ReadableStream，因为题目较长且带图片，S1 的 POST /api/chat/stream 不能用 EventSource。
 */
export interface SseEvent {
  event: string
  data: string
}

function parseEventBlock(block: string): SseEvent | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  if (dataLines.length === 0) return null
  return { event, data: dataLines.join('\n') }
}

export async function consumeSse(
  response: Response,
  onEvent: (event: SseEvent) => void,
): Promise<void> {
  if (!response.ok) {
    throw new Error(`请求失败：HTTP ${response.status}`)
  }
  if (!response.body) {
    throw new Error('当前环境不支持流式响应（ReadableStream 不可用）')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  /**
   * CR-049 / D-15：后端正常结束一定会发 done 或 error。若流被中途关闭（SSE 超时、
   * 代理断连、进程重启）而两者都没有，改造前 consumeSse 会静默返回，
   * 上层误判为成功并清空已收到的正文 —— 页面于是「没有回答也没有错误」。
   */
  let sawTerminal = false
  const deliver = (parsed: SseEvent) => {
    if (parsed.event === 'done' || parsed.event === 'error') sawTerminal = true
    onEvent(parsed)
  }
  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    let separator = buffer.indexOf('\n\n')
    while (separator !== -1) {
      const block = buffer.slice(0, separator)
      buffer = buffer.slice(separator + 2)
      const parsed = parseEventBlock(block)
      if (parsed) deliver(parsed)
      separator = buffer.indexOf('\n\n')
    }
  }
  buffer += decoder.decode()
  const tail = parseEventBlock(buffer.replace(/\r\n/g, '\n'))
  if (tail) deliver(tail)
  if (!sawTerminal) {
    throw new Error('生成连接已中断：未收到完成或失败事件（可能超时或网络断开）')
  }
}
