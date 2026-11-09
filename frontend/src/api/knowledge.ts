import { unwrap } from './client'

export interface KnowledgePoint {
  id: number
  subject: string
  parentName: string
  name: string
  hitCount: number
  mastery: number | null
  masteryLabel: string
}

export async function listKnowledge(subject?: string): Promise<KnowledgePoint[]> {
  const query = subject ? `?subject=${encodeURIComponent(subject)}` : ''
  return unwrap<KnowledgePoint[]>(await fetch(`/api/knowledge${query}`))
}
