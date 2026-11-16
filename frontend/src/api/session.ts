import { get, unwrap } from './client'

export interface SessionItem {
  id: number
  title: string
  guideMode: number
  /** CR-012：该会话选用的模型条目 id；null 表示回退全局默认。 */
  modelId: number | null
  /** CR-018：AUTO 模型自选 / MANUAL 指定学科 / OFF 不使用学科规范。 */
  subjectMode: string
  /** CR-018：首轮判定或手选后固化的学科规范 id。 */
  subjectSkillId: number | null
  /** CR-038：会话固化的通用技能 id；空数组表示「自动」（模型按需读入）。 */
  skillIds: number[]
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
  /** CR-018：该回答的输出结构；null 按默认六段，FREE_TEXT 为追问自由文本。 */
  sectionSchema: string | null
  /** CR-018：该回答采用的「学科 · 结构」标签。 */
  subjectLabel: string | null
  /** CR-039：本轮回答形态；历史消息为 null（按 sectionSchema 推断）。 */
  intent: 'SOLVE' | 'FOLLOW_UP' | 'EXPLAIN' | null
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
  return get<SessionPage>('/api/sessions/page', { page, size })
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

/** CR-018：会话级学科三态，只影响当前会话。 */
export async function setSessionSubject(
  id: number, mode: string, subjectSkillId: number | null,
): Promise<SessionItem> {
  return unwrap<SessionItem>(await fetch(`/api/sessions/${id}/subject`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ mode, subjectSkillId }),
  }))
}

/** CR-038：设置 / 清除当前会话固化的通用技能（空数组回到模型按需），最多 2 条。 */
export async function setSessionSkills(id: number, skillIds: number[]): Promise<SessionItem> {
  return unwrap<SessionItem>(await fetch(`/api/sessions/${id}/skills`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ skillIds }),
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
