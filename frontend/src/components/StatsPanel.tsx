import { useCallback, useEffect, useState } from 'react'
import { getLearningStats, type LearningStats } from '../api/stats'

/** T-305 学情统计：不引入图表库，用文本 + CSS 条形呈现（DD-11）。 */
export default function StatsPanel() {
  const [stats, setStats] = useState<LearningStats | null>(null)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setStats(await getLearningStats())
  }, [])

  useEffect(() => {
    void refresh().catch((e) => setError(String(e)))
  }, [refresh])

  const maxDay = Math.max(1, ...(stats?.byDay.map((d) => d.count) ?? [0]))
  const maxSubject = Math.max(1, ...(stats?.bySubject.map((d) => d.count) ?? [0]))

  return (
    <section className="card">
      <h2>学情统计</h2>
      <p className="muted">
        全部为只读聚合：题量与 token 来自会话与调用日志，学科分布来自知识点命中。
        未开启知识点抽取时学科分布为空，不伪造数据。
      </p>
      {error && <p className="error banner">加载失败：{error}</p>}

      {stats && (
        <>
          <div className="stats-grid">
            <div><span className="muted">会话数</span><strong>{stats.sessions}</strong></div>
            <div><span className="muted">提问数</span><strong>{stats.questions}</strong></div>
            <div><span className="muted">成功解答</span><strong>{stats.answers}</strong></div>
            <div><span className="muted">失败轮次</span><strong>{stats.failedAnswers}</strong></div>
            <div><span className="muted">输入 token</span><strong>{stats.promptTokens}</strong></div>
            <div><span className="muted">输出 token</span><strong>{stats.completionTokens}</strong></div>
            <div><span className="muted">平均耗时</span><strong>{(stats.avgDurationMs / 1000).toFixed(1)}s</strong></div>
            <div><span className="muted">当前学科</span><strong>{stats.currentSubject}</strong></div>
          </div>

          <h3>最近 7 天提问量</h3>
          {stats.byDay.length === 0 ? <p className="muted">近 7 天没有提问记录</p> : (
            <ul className="bar-list">
              {stats.byDay.map((day) => (
                <li key={day.date}>
                  <span className="bar-label">{day.date.slice(5)}</span>
                  <span className="bar" style={{ width: `${(day.count / maxDay) * 100}%` }} />
                  <span className="bar-value">{day.count}</span>
                </li>
              ))}
            </ul>
          )}

          <h3>学科分布（按知识点命中）</h3>
          {stats.bySubject.length === 0 ? <p className="muted">暂无数据（需开启知识点回顾）</p> : (
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
        </>
      )}
    </section>
  )
}
