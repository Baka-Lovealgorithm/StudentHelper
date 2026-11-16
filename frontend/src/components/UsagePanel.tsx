import { useEffect, useState } from 'react'
import { fetchUsageSummary, type UsageSummary } from '../api/usage'
import BarChart, { type BarDatum } from './BarChart'

const WINDOWS = [
  { value: 0, label: '全部' },
  { value: 1, label: '最近 1 天' },
  { value: 3, label: '最近 3 天' },
  { value: 7, label: '最近 7 天' },
] as const

/** 按天趋势可切换的两个口径（不做金额换算，FR-206 边界不变）。 */
const METRICS = [
  { key: 'calls', label: '调用次数', unit: '次' },
  { key: 'tokens', label: 'Token', unit: ' token' },
] as const

type MetricKey = typeof METRICS[number]['key']

function shortDay(day: string): string {
  return day.length >= 10 ? day.slice(5).replace('-', '/') : day
}

/** CR-065：命中率 = 命中 / (命中 + 未命中)；网关未回传缓存字段时两者皆 0，显示「—」。 */
function hitRate(hit: number, miss: number): string {
  const total = hit + miss
  return total > 0 ? `${((hit / total) * 100).toFixed(1)}%` : '—'
}

/**
 * CR-023：窗口为 1/3/7 时按天补齐缺失日期（计 0），让柱状图 X 轴与学情统计一样连续。
 * 纯展示层补齐，不改后端返回、不影响「合计 = 按模型聚合」的对账口径。
 */
function fillDaily(daily: UsageSummary['daily'], days: number): UsageSummary['daily'] {
  if (days <= 0) return daily
  const indexed = new Map(daily.map((item) => [item.day, item]))
  const today = new Date()
  const filled: UsageSummary['daily'] = []
  for (let offset = days - 1; offset >= 0; offset--) {
    const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() - offset)
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    filled.push(indexed.get(key)
      ?? { day: key, calls: 0, successCalls: 0, failedCalls: 0, promptTokens: 0,
        completionTokens: 0, cacheHitTokens: 0, cacheMissTokens: 0 })
  }
  return filled
}

/**
 * 用量看板（T-206 / FR-206 / CR-014；CR-023 与学情统计统一呈现）。
 * CR-058：三类信息改为同页分区展示（复用学情统计的 .stats-section），删除互斥页签；
 * 「按模型」6 列表格保留，窄窗口由 .usage-table-wrap 内部横向滚动。仍不做金额换算。
 */
export default function UsagePanel() {
  const [days, setDays] = useState<number>(0)
  const [data, setData] = useState<UsageSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [metric, setMetric] = useState<MetricKey>('calls')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    void fetchUsageSummary(days)
      .then((result) => { if (active) setData(result) })
      .catch((e: unknown) => { if (active) setError(String(e)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [days])

  const rows = data?.rows ?? []
  const daily = data?.daily ?? []
  const totalCalls = rows.reduce((sum, row) => sum + row.calls, 0)
  const totalSuccess = rows.reduce((sum, row) => sum + row.successCalls, 0)
  const totalFailed = rows.reduce((sum, row) => sum + row.failedCalls, 0)
  const totalTokens = rows.reduce((sum, row) => sum + row.promptTokens + row.completionTokens, 0)

  const activeMetric = METRICS.find((item) => item.key === metric) ?? METRICS[0]
  const windowLabel = WINDOWS.find((window) => window.value === days)?.label ?? '全部'
  const bars: BarDatum[] = fillDaily(daily, days).map((item) => ({
    key: item.day,
    label: shortDay(item.day),
    value: metric === 'calls' ? item.calls : item.promptTokens + item.completionTokens,
  }))

  return (
    <section className="card usage-card">
      <div className="panel-head">
        <div>
          <h2>用量看板</h2>
          <p className="muted">按模型聚合调用次数、token 用量、缓存命中与平均耗时；只统计真实调用，不做金额换算。</p>
        </div>
        <label className="usage-window">
          <span className="muted">时间范围</span>
          <select aria-label="用量统计时间范围" value={days}
            onChange={(event) => setDays(Number(event.target.value))}>
            {WINDOWS.map((window) => (
              <option key={window.value} value={window.value}>{window.label}</option>
            ))}
          </select>
        </label>
      </div>

      {error && <p className="error banner">加载失败：{error}</p>}

      {loading && <p className="muted">加载中…</p>}
      {!loading && !error && rows.length === 0 && <p className="muted">该时间范围内暂无调用记录。</p>}

      {!loading && rows.length > 0 && (
        <div className="stats-sections">
          <section className="stats-section">
            <div className="stats-section-head">
              <h3 className="stats-section-title">简要信息</h3>
            </div>
            <div className="stats-grid">
              <div><span className="muted">调用次数</span><strong>{totalCalls}</strong></div>
              <div><span className="muted">成功 / 失败</span><strong>{totalSuccess} / {totalFailed}</strong></div>
              <div><span className="muted">Token 合计</span><strong>{totalTokens}</strong></div>
              <div><span className="muted">涉及模型</span><strong>{rows.length}</strong></div>
            </div>
          </section>

          <section className="stats-section">
            <div className="stats-section-head">
              <h3 className="stats-section-title">按天趋势</h3>
              <span className="stats-section-note">{windowLabel}</span>
              <div className="metric-toggle" role="tablist" aria-label="按天趋势口径">
                {METRICS.map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    role="tab"
                    aria-selected={metric === item.key}
                    className={metric === item.key ? 'metric-toggle-btn active' : 'metric-toggle-btn'}
                    onClick={() => setMetric(item.key)}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
            <BarChart data={bars} unit={activeMetric.unit} emptyText="该时间范围内没有调用记录" />
            <p className="muted chart-foot">
              合计 <strong>{metric === 'calls' ? totalCalls : totalTokens}</strong>
              {activeMetric.unit}，与上方按模型聚合一致。
            </p>
          </section>

          <section className="stats-section">
            <div className="stats-section-head">
              <h3 className="stats-section-title">按模型</h3>
            </div>
            <div className="usage-table-wrap">
              <table className="usage-table">
                <thead>
                  <tr>
                    <th>模型</th>
                    <th>调用次数</th>
                    <th>成功 / 失败</th>
                    <th>Prompt token</th>
                    <th>Completion token</th>
                    <th>缓存命中</th>
                    <th>命中率</th>
                    <th>平均耗时</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.modelName}>
                      <td className="usage-model">{row.modelName}</td>
                      <td>{row.calls}</td>
                      <td>{row.successCalls} / {row.failedCalls}</td>
                      <td>{row.promptTokens}</td>
                      <td>{row.completionTokens}</td>
                      <td>{row.cacheHitTokens}</td>
                      <td>{hitRate(row.cacheHitTokens, row.cacheMissTokens)}</td>
                      <td>{(row.avgDurationMs / 1000).toFixed(2)} s</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      )}
    </section>
  )
}
