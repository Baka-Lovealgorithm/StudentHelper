import { del, get, post } from './client'

/** CR-040：掌握度已退役，列表不再有 mastery / masteryLabel；新增 createdAt 供日期核对。 */
export interface KnowledgePoint {
  id: number
  subject: string
  parentName: string
  name: string
  hitCount: number
  /** yyyy-MM-dd HH:mm；服务端格式化，null 仅出现在异常数据。 */
  createdAt: string | null
}

/** CR-040：三个条件同时生效；from / to 为 yyyy-MM-dd 闭区间，可只填一端。 */
export interface KnowledgeQuery {
  subject?: string
  from?: string
  to?: string
}

export async function listKnowledge(query: KnowledgeQuery = {}): Promise<KnowledgePoint[]> {
  return get<KnowledgePoint[]>('/api/knowledge', { ...query })
}

/** CR-040：分页结果，与后端 `KnowledgePageResponse` 同构。 */
export interface KnowledgePage {
  items: KnowledgePoint[]
  total: number
  hasMore: boolean
}

/** CR-040：分页查询（页面「更多」走这个）；筛选口径与 listKnowledge 完全一致。 */
export async function listKnowledgePage(
  query: KnowledgeQuery,
  page: number,
  size = 10,
): Promise<KnowledgePage> {
  return get<KnowledgePage>('/api/knowledge/page', { ...query, page, size })
}

/** CR-025：学科去重清单，供知识点页学科下拉使用。 */
export async function listKnowledgeSubjects(): Promise<string[]> {
  return get<string[]>('/api/knowledge/subjects')
}

/** CR-040：手动新增；命中次数由服务端固定为 0，重复时抛「知识点已存在」。 */
export async function createKnowledge(payload: {
  subject: string
  parentName: string
  name: string
}): Promise<KnowledgePoint> {
  return post<KnowledgePoint>('/api/knowledge', payload)
}

/** CR-040：删除条目并级联清理命中明细。 */
export async function deleteKnowledge(id: number): Promise<void> {
  await del(`/api/knowledge/${id}`)
}
