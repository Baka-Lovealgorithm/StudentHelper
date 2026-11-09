import { unwrap } from './client'

export interface OptionalSections {
  idea: boolean
  knowledge: boolean
  code: boolean
}

export async function getOptionalSections(): Promise<OptionalSections> {
  return unwrap<OptionalSections>(await fetch('/api/optional-sections'))
}

export async function setOptionalSections(patch: Partial<OptionalSections>): Promise<OptionalSections> {
  return unwrap<OptionalSections>(await fetch('/api/optional-sections', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(patch),
  }))
}
