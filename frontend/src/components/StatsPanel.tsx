import { useEffect, useState } from 'react'
import { getLearningStats, type LearningStats } from '../api/stats'
import BarChart, { type BarDatum } from './BarChart'

/** 时间窗口：0 = 全部，1 / 3 / 7 = 最近 N 个自然日。 */
const WINDOWS = [
  { value: 0, label: '全部' },
  { value: 1, label: '最近 1 天' },
  { value: 3, label: '最近 3 天' },
  { value: 7, label: '最近 7 天' },
] as const

const METRICS = [
  { key: 'questions', label: '提问量', unit: '次' },
  { key: 'answers', label: '成功解答', unit: '次' },
  { key: 'failed', label: '失败轮次', unit: '次' },
] as const

type MetricKey = typeof METRICS[number]['key']

/** `2026-11-11` → `11/11`，横轴放短标签。 */
function shortDay(date: string): string {
  return date.length >= 10 ? date.slice(5).replace('-', '/') : date
}

/**
 * T-305 学情统计（CR-023 改版）：竖柱图 + 时间过滤。
 * CR-056：三类信息改为同页分区展示（独立卡片 + 编号标题），删除互斥页签。
 * 只读聚合，不引图表库（DD-11）；**不展示 token 与耗时**，那些只在用量看板。
 */
export default function StatsPanel() {
  const [days, setDays] = useState<number>(7)
  const [stats, setStats] = useState<LearningStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [metric, setMetric] = useState<MetricKey>('questions')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    void getLearningStats(days)
      .then((result) => { if (active) setStats(result) })
      .catch((e: unknown) => { if (active) setError(String(e)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [days])

  const maxSubject = Math.max(1, ...(stats?.bySubject.map((row) => row.count) ?? [0]))
  const bars: BarDatum[] = (stats?.byDay ?? []).map((day) => ({
    key: day.date,
    label: shortDay(day.date),
    value: day[metric],
  }))
  const activeMetric = METRICS.find((item) => item.key === metric) ?? METRICS[0]
  const windowLabel = WINDOWS.find((item) => item.value === days)?.label ?? '最近 7 天'

  return (
    <section className="card">
      <div className="panel-head">
        <div>
          <h2>学情统计</h2>
          <p className="muted">
            只读聚合：题量来自会话与消息，学科分布来自知识点命中。
            三类信息同页分区展示，共用<strong>同一时间窗口</strong>，可逐项对账。
          </p>
        </div>
        <label className="usage-window">
          <span className="muted">时间范围</span>
          <select aria-label="学情统计时间范围" value={days}
            onChange={(event) => setDays(Number(event.target.value))}>
            {WINDOWS.map((item) => (
              <option key={item.value} value={item.value}>{item.label}</option>
            ))}
          </select>
        </label>
      </div>

      {error && <p className="error banner">加载失败：{error}</p>}

      {loading && <p className="muted">加载中…</p>}

      {!loading && stats && (
        <div className="stats-sections">
          <section className="stats-section">
            <div className="stats-section-head">
              <h3 className="stats-section-title">简要信息</h3>
            </div>
            <div className="stats-grid">
              <div><span className="muted">会话数</span><strong>{stats.sessions}</strong></div>
              <div><span className="muted">提问数</span><strong>{stats.questions}</strong></div>
              <div><span className="muted">成功解答</span><strong>{stats.answers}</strong></div>
              <div><span className="muted">失败轮次</span><strong>{stats.failedAnswers}</strong></div>
            </div>
          </section>

          <section className="stats-section">
            <div className="stats-section-head">
              <h3 className="stats-section-title">按天分布</h3>
              <span className="stats-section-note">{windowLabel}</span>
              <div className="metric-toggle" role="tablist" aria-label="提问量指标">
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
            <BarChart data={bars} unit={activeMetric.unit}
              emptyText={`${windowLabel}内没有提问记录`} />
            <p className="muted chart-foot">
              {activeMetric.label}合计 <strong>{bars.reduce((sum, item) => sum + item.value, 0)}</strong> {activeMetric.unit}
              {days === 0 ? ' · 「全部」时最多展示最近 30 天' : ''}
            </p>
          </section>

          <section className="stats-section">
            <div className="stats-section-head">
              <h3 className="stats-section-title">学科分布</h3>
            </div>
            {stats.bySubject.length === 0 ? (
              <p className="muted">暂无数据（需开启知识点回顾）</p>
            ) : (
              <ul className="bar-list">
                {stats.bySubject.map((row) => (
                  <li key={row.subject}>
                    <span className="bar-label">{row.subject}</span>
                    <span className="bar" style={{ width: `${(row.count / maxSubject) * 100}%` }} />
                    <span className="bar-value">{row.count}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </section>
  )
}
