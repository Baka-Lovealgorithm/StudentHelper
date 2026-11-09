import { useCallback, useEffect, useState } from 'react'
import { activateSubject, createSubject, deleteSubject, listSubjects, type SubjectItem } from '../api/subjects'

/** T-307 学科扩展（DD-08）：可配置学科模板，当前学科决定解题 Prompt。 */
export default function SubjectPanel() {
  const [items, setItems] = useState<SubjectItem[]>([])
  const [name, setName] = useState('')
  const [hint, setHint] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setItems(await listSubjects())
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
      <h2>学科扩展</h2>
      <p className="muted">
        当前学科会替换解题 Prompt 的首行与术语要求，并在末尾追加「学科补充」。
        六段（①~⑥）结构与串题约束不随学科变化；关闭开关后回到默认「数据结构」。
      </p>
      {error && <p className="error banner">操作失败：{error}</p>}
      {notice && <p className="muted">{notice}</p>}

      <ul className="knowledge-list">
        {items.map((item) => (
          <li key={item.id}>
            <div>
              <strong>{item.name}</strong>
              {item.current && <span className="tag">当前</span>}
              {item.promptHint && <p className="muted">{item.promptHint}</p>}
            </div>
            <div className="message-actions">
              <button type="button" className="btn" disabled={item.current}
                onClick={() => run(() => activateSubject(item.id), `已切换到「${item.name}」`)}>
                设为当前
              </button>
              <button type="button" className="btn" disabled={item.current}
                onClick={() => run(() => deleteSubject(item.id), `已删除「${item.name}」`)}>
                删除
              </button>
            </div>
          </li>
        ))}
        {items.length === 0 && <li className="muted">暂无学科</li>}
      </ul>

      <h3>新增学科</h3>
      <label className="field">
        学科名称
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="例如：线性代数" />
      </label>
      <label className="field">
        学科补充（可选）
        <input value={hint} onChange={(e) => setHint(e.target.value)} placeholder="例如：矩阵变换要写清步骤" />
      </label>
      <div className="message-actions">
        <button type="button" className="btn" onClick={() => run(async () => {
          await createSubject(name, hint)
          setName('')
          setHint('')
        }, '学科已新增')}>
          新增学科
        </button>
      </div>
    </section>
  )
}
