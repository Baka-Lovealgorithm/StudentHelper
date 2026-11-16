import { unwrap } from './client'
import { parseSections } from '../features/solution/sections'

/** 兼容既有引用：渲染规则已移到 `features/solution/sections.ts`（CR-043）。 */
export { parseSections }

/** CR-018：学科规范条目（知识由模型提供，这里只放回答规范与输出结构）。 */
export interface SubjectSkillItem {
  id: number
  code: string | null
  name: string
  description: string | null
  scope: 'BUILTIN' | 'CUSTOM' | string
  enabled: boolean
  keywords: string | null
  specMd: string
  sectionsJson: string
  /** CR-039：三段规范（solve / followUp / explain）的 JSON 字符串；空段回落系统默认。 */
  specJson: string | null
  createdAt: string
  updatedAt: string
}

export interface SubjectSkillPayload {
  name: string
  description?: string
  keywords?: string
  specMd: string
  sectionsJson: string
  /** CR-039：三段规范 JSON；提供时以它为准，specMd 仅作 solve 的兼容入参。 */
  specJson?: string
  enabled?: boolean
}

export type SubjectMode = 'AUTO' | 'MANUAL' | 'OFF'

export async function listSubjectSkills(): Promise<SubjectSkillItem[]> {
  return unwrap<SubjectSkillItem[]>(await fetch('/api/subject-skills'))
}

export async function createSubjectSkill(payload: SubjectSkillPayload): Promise<SubjectSkillItem> {
  return unwrap<SubjectSkillItem>(await fetch('/api/subject-skills', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }))
}

export async function updateSubjectSkill(id: number, payload: SubjectSkillPayload): Promise<SubjectSkillItem> {
  return unwrap<SubjectSkillItem>(await fetch(`/api/subject-skills/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }))
}

export async function deleteSubjectSkill(id: number): Promise<{ deleted: boolean }> {
  return unwrap<{ deleted: boolean }>(await fetch(`/api/subject-skills/${id}`, { method: 'DELETE' }))
}

/** 任意条目均可启停（CR-060 起取消内置只读概念）。 */
export async function setSubjectSkillEnabled(id: number, enabled: boolean): Promise<SubjectSkillItem> {
  return unwrap<SubjectSkillItem>(await fetch(`/api/subject-skills/${id}/enabled`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  }))
}

/** SKILL.md 文件夹导入（files[] + webkitRelativePath 的 JSON 数组）。 */
export async function importSubjectSkillFolder(form: FormData): Promise<SubjectSkillItem> {
  return unwrap<SubjectSkillItem>(await fetch('/api/subject-skills/import', { method: 'POST', body: form }))
}

/** CR-039：三段规范的解析与序列化（空串表示「留空，用系统默认」）。 */
export interface SubjectSpecs {
  solve: string
  followUp: string
  explain: string
}

export function parseSpecs(item: SubjectSkillItem): SubjectSpecs {
  if (item.specJson) {
    try {
      const parsed = JSON.parse(item.specJson) as Partial<SubjectSpecs>
      return {
        solve: typeof parsed.solve === 'string' ? parsed.solve : (item.specMd ?? ''),
        followUp: typeof parsed.followUp === 'string' ? parsed.followUp : '',
        explain: typeof parsed.explain === 'string' ? parsed.explain : '',
      }
    } catch { /* 脏数据按旧单段处理 */ }
  }
  return { solve: item.specMd ?? '', followUp: '', explain: '' }
}
