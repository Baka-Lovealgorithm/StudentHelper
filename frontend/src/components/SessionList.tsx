import { useEffect, useRef, useState } from 'react'
import type { SessionItem } from '../api/session'
import RowActionMenu from './RowActionMenu'

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

/**
 * CR-014：新建会话与标题同行且固定；时间靠右、操作按钮悬停显现。
 * CR-034：时间只显 `MM-DD`；悬停时时间位被单个编辑入口覆盖，点击弹「重命名 / 删除」菜单。
 */
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
      <div className="session-head">
        <h2 className="panel-title">会话</h2>
        <button type="button" className="btn primary small" onClick={onCreate}>＋ 新建会话</button>
      </div>
      {/* CR-036：头部固定在滚动容器之外，滚动内容不可能穿透到「新建会话」行上方。 */}
      <div className="session-scroll">
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
                <>
                  <button type="button" className="session-item" onClick={() => onSelect(session.id)}>
                    <span className="session-title">{session.title}</span>
                    <small className="session-time">{session.updatedAt.slice(5, 10)}</small>
                  </button>
                  <RowActionMenu
                    label={`会话操作：${session.title}`}
                    items={[
                      { key: 'rename', label: '重命名', onSelect: () => startEdit(session) },
                      { key: 'delete', label: '删除会话', danger: true, onSelect: () => onDelete(session.id) },
                    ]}
                  />
                </>
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
    </div>
  )
}
