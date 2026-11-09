import { useEffect, useState } from 'react'
import { fetchUsageSummary, type UsageRow } from '../api/usage'

/** 用量看板（T-206 / FR-206）：按模型聚合调用次数、token 与平均耗时，不做金额换算。 */
export default function UsagePanel() {
  const [rows, setRows] = useState<UsageRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    void fetchUsageSummary()
      .then(setRows)
      .catch((e: unknown) => setError(String(e)))
      .finally(() => setLoading(false))
  }, [])

  return (
    <section className="card usage-card">
      <h2>用量看板</h2>
      <p className="muted">按模型聚合调用次数、token 用量与平均耗时；只统计本地调用日志，不做金额换算。</p>

      {error && <p className="error banner">加载失败：{error}</p>}
      {loading && <p className="muted">加载中…</p>}
      {!loading && !error && rows.length === 0 && <p className="muted">暂无调用记录。</p>}

      {!loading && rows.length > 0 && (
        <table className="usage-table">
          <thead>
            <tr>
              <th>模型</th>
              <th>调用次数</th>
              <th>成功 / 失败</th>
              <th>Prompt token</th>
              <th>Completion token</th>
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
                <td>{(row.avgDurationMs / 1000).toFixed(2)} s</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
