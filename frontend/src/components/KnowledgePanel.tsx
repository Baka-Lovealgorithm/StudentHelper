import { useCallback, useEffect, useState } from 'react'
import { listKnowledge, type KnowledgePoint } from '../api/knowledge'

/** T-203：两级知识点、命中次数与掌握度；mastery 为 null 显示「未评估」（AC-203-3）。 */
export default function KnowledgePanel() {
  const [rows, setRows] = useState<KnowledgePoint[]>([])
  const [subject, setSubject] = useState('')
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setRows(await listKnowledge(subject || undefined))
  }, [subject])

  useEffect(() => {
    void refresh().catch((e) => setError(String(e)))
  }, [refresh])

  return (
    <section className="card">
      <h2>知识点回顾</h2>
      <p className="muted">
        由模型从每轮解答的「② 涉及知识点」抽取，按大知识点 → 小知识点两级聚合。
        掌握度 = 举一反三 PASS 数 / 已判分数；样本不足 3 次显示「未评估」。
      </p>
      <div className="knowledge-filter">
        <label>
          学科
          <select value={subject} onChange={(e) => setSubject(e.target.value)}>
            <option value="">全部</option>
            <option value="数据结构">数据结构</option>
          </select>
        </label>
        <button type="button" className="btn small" onClick={() => void refresh()}>刷新</button>
        <span className="muted">共 {rows.length} 个知识点</span>
      </div>
      {error && <p className="error banner">加载失败：{error}</p>}
      {rows.length === 0 ? (
        <p className="muted">暂无知识点记录。开启「知识点回顾」开关并解答题目后会自动抽取。</p>
      ) : (
        <div className="knowledge-list">
          {rows.map((row) => (
            <div key={row.id} className="knowledge-row">
              <span className="kp-major">{row.parentName || '（未分级）'}</span>
              <span className="kp-arrow">→</span>
              <span className="kp-name">{row.name}</span>
              <span className="kp-hit">命中 {row.hitCount} 次</span>
              <span className={`kp-mastery ${row.mastery === null ? 'empty' : ''}`}>
                {row.masteryLabel}
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
