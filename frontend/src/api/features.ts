import { unwrap } from './client'

export interface FeatureToggle {
  key: string
  label: string
  task: string
  enabled: boolean
}

export async function listFeatures(): Promise<FeatureToggle[]> {
  return unwrap<FeatureToggle[]>(await fetch('/api/features'))
}

export async function setFeature(key: string, enabled: boolean): Promise<FeatureToggle> {
  return unwrap<FeatureToggle>(await fetch(`/api/features/${key}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  }))
}
