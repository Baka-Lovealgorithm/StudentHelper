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

/** CR-011：单独启停，不需要重传名称与正文。 */
export async function setSkillEnabled(id: number, enabled: boolean): Promise<SkillItem> {
  return unwrap<SkillItem>(await fetch(`/api/skills/${id}/enabled`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  }))
}

/** CR-011：浏览器选文件夹上传（files[] + 相对路径 JSON 数组）。 */
export async function importSkillFolder(form: FormData): Promise<SkillItem> {
  return unwrap<SkillItem>(await fetch('/api/skills/import', { method: 'POST', body: form }))
}
