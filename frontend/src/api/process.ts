import { unwrap } from './client'

/** CR-027：一条模型行为过程；流式推送时 messageId 为 null，回读时带消息 id。 */
export interface ProcessItem {
  messageId: number | null
  seq: number
  kind: string
  title: string
  detail: string
  elapsedMs: number
}

/** 历史图片相对路径（如 317/uuid.png）转成只读端点 URL。 */
export function imageUrl(path: string): string {
  const [sessionId, fileName] = path.trim().split('/')
  return `/api/images/${encodeURIComponent(sessionId)}/${encodeURIComponent(fileName)}`
}

export async function listProcess(sessionId: number): Promise<ProcessItem[]> {
  return unwrap<ProcessItem[]>(await fetch(`/api/process?sessionId=${sessionId}`))
}

/** 按 messageId 分组；messageId 缺失的行忽略（只在流式期出现，不落库）。 */
export function groupProcess(items: ProcessItem[]): Record<number, ProcessItem[]> {
  const map: Record<number, ProcessItem[]> = {}
  for (const item of items) {
    if (item.messageId == null) continue
    ;(map[item.messageId] ??= []).push(item)
  }
  return map
}
