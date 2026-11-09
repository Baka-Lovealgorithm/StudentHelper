import { unwrap } from './client'

export interface SessionItem {
  id: number
  title: string
  guideMode: number
  /** CR-012：该会话选用的模型条目 id；null 表示回退全局默认。 */
  modelId: number | null
  createdAt: string
  updatedAt: string
}

export interface MessageItem {
  id: number
  role: string
  content: string | null
  hint: string | null
  imagePaths: string | null
  status: string
  errorCode: string | null
  createdAt: string
}

export interface SessionDetail {
  session: SessionItem
  messages: MessageItem[]
}

/** 分页结果：只带 total / hasMore，不提供页码跳转。 */
export interface SessionPage {
  items: SessionItem[]
  total: number
  hasMore: boolean
}

export async function listSessions(): Promise<SessionItem[]> {
  return unwrap<SessionItem[]>(await fetch('/api/sessions'))
}

export async function listSessionsPage(page = 0, size = 10): Promise<SessionPage> {
  return unwrap<SessionPage>(await fetch(`/api/sessions/page?page=${page}&size=${size}`))
}

export async function createSession(title?: string): Promise<SessionItem> {
  return unwrap<SessionItem>(await fetch('/api/sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title: title ?? '' }),
  }))
}

export async function getSessionDetail(id: number): Promise<SessionDetail> {
  return unwrap<SessionDetail>(await fetch(`/api/sessions/${id}`))
}

export async function deleteSession(id: number): Promise<void> {
  await unwrap<unknown>(await fetch(`/api/sessions/${id}`, { method: 'DELETE' }))
}

export async function setGuideMode(id: number, guideMode: number): Promise<SessionItem> {
  return unwrap<SessionItem>(await fetch(`/api/sessions/${id}/guide-mode`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ guideMode }),
  }))
}

export async function renameSession(id: number, title: string): Promise<SessionItem> {
  return unwrap<SessionItem>(await fetch(`/api/sessions/${id}/title`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  }))
}

/** CR-012：只切换当前会话选用的模型，不影响其它会话。 */
export async function setSessionModel(id: number, modelId: number): Promise<SessionItem> {
  return unwrap<SessionItem>(await fetch(`/api/sessions/${id}/model`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ modelId }),
  }))
}
