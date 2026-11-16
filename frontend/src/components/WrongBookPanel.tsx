import { useCallback, useEffect, useState } from 'react'
import { deleteWrongBook, listWrongBook, type WrongBookItem } from '../api/wrongBook'
import { listSubjectSkills } from '../api/subjectSkills'
import RowActionMenu from './RowActionMenu'
import ConfirmDialog from './ConfirmDialog'

interface Props {
  onOpenSession?: (sessionId: number) => void
}

/** 纯图片题等场景下题面正文可能为空，占位而不是让渲染崩掉（D-12）。 */
const EMPTY_QUESTION = '（该题无文字内容，可能是纯图片题）'

function formatTime(value: string | null | undefined): string | null {
  if (!value) return null
  const text = value.replace('T', ' ')
  return text.length >= 19 ? text.slice(0, 19) : text
}

/** T-202 错题本：复用 t_message_feedback 的 WRONG 标记，按学科 / 时间过滤；支持单条删除（CR-022）。
 *  CR-061：学科筛选由自由文本改为下拉（选项 = 学科规范清单 ∪ 错题里出现的学科），避免精确匹配查不到。 */
export default function WrongBookPanel({ onOpenSession }: Props) {
  const [items, setItems] = useState<WrongBookItem[]>([])
  const [subject, setSubject] = useState('')
  const [subjectOptions, setSubjectOptions] = useState<string[]>([])
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  /** CR-069：待移除的错题（行菜单先弹居中确认框，替代原生 confirm）。 */
  const [deleteTarget, setDeleteTarget] = useState<WrongBookItem | null>(null)

  /** CR-061：下拉选项 = 学科规范清单 ∪ 错题里已出现的学科（去重 + 中文排序）。 */
  const mergeOptions = useCallback((more: (string | null | undefined)[]) => {
    setSubjectOptions((prev) => {
      const merged = new Set(prev)
      more.forEach((value) => {
        const text = value?.trim()
        if (text) merged.add(text)
      })
      const next = [...merged].sort((a, b) => a.localeCompare(b, 'zh'))
      return next.length === prev.length && next.every((v, i) => v === prev[i]) ? prev : next
    })
  }, [])

  const refresh = useCallback(async () => {
    const list = await listWrongBook({ subject: subject || undefined, from: from || undefined, to: to || undefined })
    setItems(list)
    mergeOptions(list.map((item) => item.subject))
  }, [subject, from, to, mergeOptions])

  useEffect(() => {
    void refresh().catch((e) => setError(String(e)))
  }, [refresh])

  // CR-061：学科规范清单只取一次，失败不阻塞列表（降级为「仅数据里出现的学科」）。
  useEffect(() => {
    void listSubjectSkills()
      .then((items) => mergeOptions(items.map((item) => item.name)))
      .catch(() => undefined)
  }, [mergeOptions])

  /** CR-069：菜单点击只记目标，确认框里再真正移除。 */
  const remove = (item: WrongBookItem) => {
    setDeleteTarget(item)
  }

  const confirmRemove = () => void (async () => {
    const item = deleteTarget
    if (!item) return
    setDeleteTarget(null)
    setBusyId(item.feedbackId)
    setError(null)
    try {
      await deleteWrongBook(item.feedbackId)
      await refresh()
    } catch (e) {
      setError(String(e))
    } finally {
      setBusyId(null)
    }
  })()

  return (
    <section className="card">
      <h2>错题本 / 问题回顾</h2>
      <p className="muted">
        在会话里对某条解答点「加入错题本」，这里会记录对应的原始题面。重复标记不会产生重复行；
        移除只删这条记录，消息与会话不受影响。
      </p>
      {error && <p className="error banner">操作失败：{error}</p>}
      <div className="filter-row">
        <label className="field">学科
          <select value={subject} onChange={(e) => setSubject(e.target.value)} aria-label="按学科筛选">
            <option value="">全部学科</option>
            {subjectOptions.map((name) => <option key={name} value={name}>{name}</option>)}
          </select>
        </label>
        <label className="field">起始<input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="field">结束<input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <button type="button" className="btn" onClick={() => void refresh().catch((e) => setError(String(e)))}>筛选</button>
      </div>
      {items.length === 0 ? <p className="muted">当前条件下没有错题。</p> : (
        <ul className="plain-list">
          {items.map((item) => {
            const question = item.question ?? ''
            const created = formatTime(item.createdAt)
            return (
              <li key={item.feedbackId} className="wrong-item">
                <div className="muted">
                  {item.subject || '未分类'}{created ? ` · ${created}` : ''}
                </div>
                <div>{question ? (question.length > 220 ? `${question.slice(0, 220)}…` : question) : EMPTY_QUESTION}</div>
                {item.note && <div className="muted">备注：{item.note}</div>}
                <div className="row-actions">
                  {onOpenSession && (
                    <button type="button" className="btn small" onClick={() => onOpenSession(item.sessionId)}>打开会话</button>
                  )}
                  <RowActionMenu
                    label={`错题操作：${item.subject || '未分类'}`}
                    items={[{ key: 'remove', label: '从错题本移除', danger: true,
                      disabled: busyId === item.feedbackId,
                      onSelect: () => remove(item) }]}
                  />
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {/* CR-069：移除错题改用页面内居中确认框 */}
      <ConfirmDialog
        open={deleteTarget !== null}
        title="移出错题本"
        message="确认从错题本移除这道题？原始消息不会被删除，仍可在会话里重新标记。"
        confirmText="移除"
        onConfirm={confirmRemove}
        onCancel={() => setDeleteTarget(null)}
      />
    </section>
  )
}
