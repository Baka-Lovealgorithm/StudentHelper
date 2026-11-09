import { unwrap } from './client'

export interface UsageRow {
  modelName: string
  calls: number
  successCalls: number
  failedCalls: number
  promptTokens: number
  completionTokens: number
  avgDurationMs: number
}

/** 用量看板（T-206 / FR-206）：按模型聚合，后端不做金额换算，前端也不显示货币。 */
export async function fetchUsageSummary(): Promise<UsageRow[]> {
  return unwrap<UsageRow[]>(await fetch('/api/usage/summary'))
}
