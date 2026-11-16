import { unwrap } from './client'

export interface UsageRow {
  modelName: string
  calls: number
  successCalls: number
  failedCalls: number
  promptTokens: number
  completionTokens: number
  avgDurationMs: number
  /** CR-065：缓存命中 / 未命中 prompt token，用于计算命中率。 */
  cacheHitTokens: number
  cacheMissTokens: number
}

/** CR-014：按天分桶的用量小计。 */
export interface UsageDaily {
  day: string
  calls: number
  successCalls: number
  failedCalls: number
  promptTokens: number
  completionTokens: number
  /** CR-065：与按模型聚合同源。 */
  cacheHitTokens: number
  cacheMissTokens: number
}

export interface UsageSummary {
  rows: UsageRow[]
  daily: UsageDaily[]
  /** 0 = 全部；1 / 3 / 7 = 最近 N 天滚动窗口。 */
  days: number
}

/** 用量看板（T-206 / FR-206 / CR-014）：按模型聚合 + 按天分桶，后端不做金额换算。 */
export async function fetchUsageSummary(days = 0): Promise<UsageSummary> {
  const query = days > 0 ? `?days=${days}` : ''
  return unwrap<UsageSummary>(await fetch(`/api/usage/summary${query}`))
}
