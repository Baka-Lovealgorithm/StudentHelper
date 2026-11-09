import type { SessionItem } from '../api/session'

interface Props {
  sessions: SessionItem[]
  currentId: number | null
  onCreate: () => void
  onSelect: (id: number) => void
  onDelete: (id: number) => void
}

export default function SessionList({ sessions, currentId, onCreate, onSelect, onDelete }: Props) {
  return (
    <div className="sidebar-inner">
      <h2 className="panel-title">会话</h2>
      <button type="button" className="btn primary full" onClick={onCreate}>＋ 新建会话</button>
      <ul className="session-list">
        {sessions.map((session) => (
          <li key={session.id} className={session.id === currentId ? 'active' : ''}>
            <button type="button" className="session-item" onClick={() => onSelect(session.id)}>
              <span className="session-title">{session.title}</span>
              <small>{session.updatedAt.slice(0, 16).replace('T', ' ')}</small>
            </button>
            <button type="button" className="icon-btn" title="删除会话" onClick={() => onDelete(session.id)}>×</button>
          </li>
        ))}
      </ul>
      {sessions.length === 0 && <p className="muted">还没有会话，点上方新建。</p>}
    </div>
  )
}
