import { unwrap } from './client'

export interface SkillItem {
  id: number
  name: string
  description: string | null
  content: string
  enabled: boolean
  updatedAt: string
}

export async function listSkills(): Promise<SkillItem[]> {
  return unwrap<SkillItem[]>(await fetch('/api/skills'))
}

export async function createSkill(payload: {
  name: string
  description?: string
  content: string
  enabled: boolean
}): Promise<SkillItem> {
  return unwrap<SkillItem>(await fetch('/api/skills', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }))
}

export async function updateSkill(id: number, payload: {
  name: string
  description?: string
  content: string
  enabled: boolean
}): Promise<SkillItem> {
  return unwrap<SkillItem>(await fetch(`/api/skills/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }))
}

export async function deleteSkill(id: number): Promise<{ deleted: boolean }> {
  return unwrap<{ deleted: boolean }>(await fetch(`/api/skills/${id}`, { method: 'DELETE' }))
}
