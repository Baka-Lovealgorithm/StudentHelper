import { useEffect, useRef, useState } from 'react'
import type { SessionItem } from '../api/session'

interface Props {
  sessions: SessionItem[]
  currentId: number | null
  hasMore: boolean
  loadingMore: boolean
  onCreate: () => void
  onSelect: (id: number) => void
  onDelete: (id: number) => void
  onRename: (id: number, title: string) => void
  onLoadMore: () => void
}

export default function SessionList({
  sessions, currentId, hasMore, loadingMore,
  onCreate, onSelect, onDelete, onRename, onLoadMore,
}: Props) {
  const [editingId, setEditingId] = useState<number | null>(null)
  const [draft, setDraft] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  /** Enter 提交会触发 input 卸载，用标记避免 blur 再提交一次。 */
  const committedRef = useRef(false)

  useEffect(() => {
    if (editingId !== null) {
      inputRef.current?.focus()
      inputRef.current?.select()
    }
  }, [editingId])

  const startEdit = (session: SessionItem) => {
    committedRef.current = false
    setDraft(session.title)
    setEditingId(session.id)
  }

  const cancel = () => {
    committedRef.current = true
    setEditingId(null)
  }

  const commit = () => {
    if (committedRef.current || editingId === null) return
    committedRef.current = true
    const id = editingId
    const title = draft.trim()
    const original = sessions.find((item) => item.id === id)?.title
    setEditingId(null)
    if (title && title !== original) onRename(id, title)
  }

  return (
    <div className="sidebar-inner">
      <h2 className="panel-title">会话</h2>
      <button type="button" className="btn primary full" onClick={onCreate}>＋ 新建会话</button>
      <ul className="session-list">
        {sessions.map((session) => (
          <li key={session.id} className={session.id === currentId ? 'active' : ''}>
            {editingId === session.id ? (
              <input
                ref={inputRef}
                className="session-rename-input"
                value={draft}
                maxLength={100}
                onChange={(event) => setDraft(event.target.value)}
                onBlur={commit}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') { event.preventDefault(); commit() }
                  else if (event.key === 'Escape') { event.preventDefault(); cancel() }
                }}
              />
            ) : (
              <button type="button" className="session-item" onClick={() => onSelect(session.id)}>
                <span className="session-title">{session.title}</span>
                <small>{session.updatedAt.slice(0, 16).replace('T', ' ')}</small>
              </button>
            )}
            {editingId !== session.id && (
              <div className="session-actions">
                <button type="button" className="icon-btn rename" title="重命名会话" onClick={() => startEdit(session)}>✎</button>
                <button type="button" className="icon-btn" title="删除会话" onClick={() => onDelete(session.id)}>×</button>
              </div>
            )}
          </li>
        ))}
      </ul>
      {sessions.length === 0 && <p className="muted">还没有会话，点上方新建。</p>}
      {hasMore && (
        <button type="button" className="btn load-more" onClick={onLoadMore} disabled={loadingMore}>
          {loadingMore ? '加载中…' : '加载更多'}
        </button>
      )}
    </div>
  )
}
