import { useCallback, useEffect, useState } from 'react'
import { createSkill, deleteSkill, listSkills, updateSkill, type SkillItem } from '../api/skills'

/** T-302 解题技能（DD-09）：启用的技能会注入 Prompt，最多 2 条、每条 800 字。 */
export default function SkillPanel() {
  const [items, setItems] = useState<SkillItem[]>([])
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setItems(await listSkills())
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
      <h2>解题技能</h2>
      <p className="muted">
        技能是一段写作 / 讲解约定，启用后追加到 System Prompt。只约束做法，不改变六段结构；
        注入最多 2 条、每条截断 800 字。
      </p>
      {error && <p className="error banner">操作失败：{error}</p>}
      {notice && <p className="muted">{notice}</p>}

      <ul className="knowledge-list">
        {items.map((item) => (
          <li key={item.id}>
            <div>
              <strong>{item.name}</strong>
              {item.enabled && <span className="tag">已启用</span>}
              {item.description && <p className="muted">{item.description}</p>}
              <pre className="profile-raw">{item.content.slice(0, 160)}{item.content.length > 160 ? '…' : ''}</pre>
            </div>
            <div className="message-actions">
              <button type="button" className="btn" onClick={() => run(() => updateSkill(item.id, {
                name: item.name,
                description: item.description ?? '',
                content: item.content,
                enabled: !item.enabled,
              }), item.enabled ? '已停用' : '已启用')}>
                {item.enabled ? '停用' : '启用'}
              </button>
              <button type="button" className="btn" onClick={() => run(() => deleteSkill(item.id), '已删除')}>
                删除
              </button>
            </div>
          </li>
        ))}
        {items.length === 0 && <li className="muted">暂无技能</li>}
      </ul>

      <h3>新增技能</h3>
      <label className="field">
        名称
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="例如：复杂度标注" />
      </label>
      <label className="field">
        说明（可选）
        <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="一句话说明用途" />
      </label>
      <label className="field">
        技能内容
        <textarea rows={5} value={content} onChange={(e) => setContent(e.target.value)}
          placeholder="例如：每个算法都显式标注时间与空间复杂度，并说明最坏情况。" />
      </label>
      <div className="message-actions">
        <button type="button" className="btn" onClick={() => run(async () => {
          await createSkill({ name, description: description || undefined, content, enabled: true })
          setName('')
          setDescription('')
          setContent('')
        }, '技能已新增并启用')}>
          新增技能
        </button>
      </div>
    </section>
  )
}
