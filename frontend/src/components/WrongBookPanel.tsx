import { useCallback, useEffect, useState } from 'react'
import { listWrongBook, type WrongBookItem } from '../api/wrongBook'

interface Props {
  onOpenSession?: (sessionId: number) => void
}

/** T-202 错题本：复用 t_message_feedback 的 WRONG 标记，按学科 / 时间过滤。 */
export default function WrongBookPanel({ onOpenSession }: Props) {
  const [items, setItems] = useState<WrongBookItem[]>([])
  const [subject, setSubject] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setItems(await listWrongBook({ subject: subject || undefined, from: from || undefined, to: to || undefined }))
  }, [subject, from, to])

  useEffect(() => {
    void refresh().catch((e) => setError(String(e)))
  }, [refresh])

  return (
    <section className="card">
      <h2>错题本 / 问题回顾</h2>
      <p className="muted">在会话里对某条解答点「加入错题本」，这里会记录对应的原始题面。重复标记不会产生重复行。</p>
      {error && <p className="error banner">加载失败：{error}</p>}
      <div className="filter-row">
        <label className="field">学科<input value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="数据结构" /></label>
        <label className="field">起始<input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="field">结束<input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <button type="button" className="btn" onClick={() => void refresh().catch((e) => setError(String(e)))}>筛选</button>
      </div>
      {items.length === 0 ? <p className="muted">当前条件下没有错题。</p> : (
        <ul className="plain-list">
          {items.map((item) => (
            <li key={item.feedbackId} className="wrong-item">
              <div className="muted">{item.subject ?? '未分类'} · {item.createdAt?.replace('T', ' ').slice(0, 19)}</div>
              <div>{item.question.length > 220 ? `${item.question.slice(0, 220)}…` : item.question}</div>
              {item.note && <div className="muted">备注：{item.note}</div>}
              {onOpenSession && (
                <button type="button" className="btn small" onClick={() => onOpenSession(item.sessionId)}>打开会话</button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
