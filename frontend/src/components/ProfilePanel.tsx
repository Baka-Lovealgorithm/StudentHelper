import { useCallback, useEffect, useState } from 'react'
import {
  addMemory, clearProfile, disableMemory, getProfile, listMemories, updateProfile,
  type MemoryItem,
} from '../api/profile'

/** T-201 跨会话长期记忆与用户画像（DD-05：显式写入，不自动抽取）。 */
export default function ProfilePanel() {
  const [raw, setRaw] = useState<string | null>(null)
  const [memories, setMemories] = useState<MemoryItem[]>([])
  const [kind, setKind] = useState('PREFERENCE')
  const [content, setContent] = useState('')
  const [depth, setDepth] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    const [profile, items] = await Promise.all([getProfile(), listMemories()])
    setRaw(profile.profileJson)
    setMemories(items)
    try {
      const parsed = profile.profileJson ? JSON.parse(profile.profileJson) as Record<string, unknown> : {}
      setDepth(typeof parsed.depth === 'string' ? parsed.depth : '')
    } catch {
      setDepth('')
    }
  }, [])

  useEffect(() => {
    void refresh().catch((e) => setError(String(e)))
  }, [refresh])

  const run = (action: () => Promise<unknown>, message: string) => void (async () => {
    setError(null)
    setNotice(null)
    try {
      await action()
      await refresh()
      setNotice(message)
    } catch (e) {
      setError(String(e))
    }
  })()

  return (
    <section className="card">
      <h2>长期记忆与画像</h2>
      <p className="muted">
        偏好由你手动写入，跨会话生效；系统不会自动从对话里抽取记忆，也不会为此额外调用模型。
        最多注入最近 3 条、每条 60 字。
      </p>
      {error && <p className="error banner">操作失败：{error}</p>}
      {notice && <p className="muted">{notice}</p>}

      <div className="profile-grid">
        <div>
          <h3>画像字段</h3>
          <label className="field">
            讲解偏好
            <input value={depth} onChange={(e) => setDepth(e.target.value)} placeholder="例如：讲通俗些，少用公式" />
          </label>
          <div className="message-actions">
            <button type="button" className="btn" onClick={() => run(() => updateProfile({ depth, subject: '数据结构' }), '画像已保存')}>
              保存画像
            </button>
            <button type="button" className="btn" onClick={() => run(clearProfile, '画像已清空，全部记忆已停用')}>
              清空画像与记忆
            </button>
          </div>
          <pre className="profile-raw">{raw ?? '（空）'}</pre>
        </div>
        <div>
          <h3>新增记忆</h3>
          <label className="field">
            类型
            <select value={kind} onChange={(e) => setKind(e.target.value)}>
              <option value="PREFERENCE">偏好 PREFERENCE</option>
              <option value="FACT">事实 FACT</option>
              <option value="SUMMARY">总结 SUMMARY</option>
            </select>
          </label>
          <label className="field">
            内容
            <textarea value={content} onChange={(e) => setContent(e.target.value)} rows={3} placeholder="例如：讲解复杂度时请给出推导过程" />
          </label>
          <button
            type="button"
            className="btn"
            disabled={!content.trim()}
            onClick={() => run(async () => {
              await addMemory(kind, content.trim())
              setContent('')
            }, '记忆已写入')}
          >
            写入记忆
          </button>
        </div>
      </div>

      <h3>记忆条目</h3>
      {memories.length === 0 ? <p className="muted">暂无记忆，写入后新会话即可生效。</p> : (
        <ul className="plain-list">
          {memories.map((item) => (
            <li key={item.id} className={item.enabled ? '' : 'muted'}>
              <span className="tag">{item.kind}</span> {item.content}
              {item.enabled && (
                <button type="button" className="btn small" onClick={() => run(() => disableMemory(item.id), '已停用')}>
                  停用
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
