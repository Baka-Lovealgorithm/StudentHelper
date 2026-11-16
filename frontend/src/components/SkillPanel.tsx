import {
  useCallback, useEffect, useRef, useState,
  type ChangeEvent,
} from 'react'
import {
  createSkill, deleteSkill, importSkillFolder, listSkills, setSkillEnabled,
  type SkillItem,
} from '../api/skills'
import RowActionMenu from './RowActionMenu'

/** T-302 技能：CR-011 文件夹导入 / 单独启停；CR-014 两个新增入口统一右上角、卡片只留描述。 */
export default function SkillPanel() {
  const [items, setItems] = useState<SkillItem[]>([])
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [importing, setImporting] = useState(false)
  const [showManual, setShowManual] = useState(false)
  const folderRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    // React 不认识 webkitdirectory 这个布尔属性，用 DOM API 设置以保证选中「文件夹」
    folderRef.current?.setAttribute('webkitdirectory', '')
    folderRef.current?.setAttribute('directory', '')
  }, [])

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

  const handleFolder = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (files.length === 0) return
    const paths = files.map((file) =>
      (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name)
    const form = new FormData()
    for (const file of files) form.append('files', file)
    form.append('paths', JSON.stringify(paths))
    setImporting(true)
    setError(null)
    void (async () => {
      try {
        const skill = await importSkillFolder(form)
        await refresh()
        setNotice(`已从文件夹导入技能「${skill.name}」，默认停用，可在此开启`)
      } catch (e) {
        setError(String(e))
      } finally {
        setImporting(false)
      }
    })()
  }

  return (
    <section className="card">
      <div className="panel-head">
        <div>
          <h2>解题技能</h2>
          <p className="muted">
            技能是一段写作 / 讲解约定，启用后追加到 System Prompt，只约束做法、不改变六段结构；
            按创建顺序固定展示，注入最多 2 条、每条截断 800 字。
          </p>
        </div>
        <div className="row-actions panel-head-actions">
          <input ref={folderRef} type="file" multiple style={{ display: 'none' }} onChange={handleFolder} />
          <button type="button" className="btn" aria-expanded={showManual}
            onClick={() => setShowManual((previous) => !previous)}>
            {showManual ? '收起新增' : '手动新增技能'}
          </button>
          <button type="button" className="btn primary" disabled={importing}
            onClick={() => folderRef.current?.click()}>
            {importing ? '导入中…' : '从文件夹导入'}
          </button>
        </div>
      </div>

      {showManual && (
        <div className="skill-manual-form">
          <label className="field">名称
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="例如：复杂度标注" />
          </label>
          <label className="field">说明（可选）
            <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="一句话说明用途" />
          </label>
          <label className="field">技能内容
            <textarea rows={5} value={content} onChange={(e) => setContent(e.target.value)}
              placeholder="例如：每个算法都显式标注时间与空间复杂度，并说明最坏情况。" />
          </label>
          <div className="message-actions">
            <button type="button" className="btn primary" onClick={() => run(async () => {
              await createSkill({ name, description: description || undefined, content, enabled: true })
              setName('')
              setDescription('')
              setContent('')
              setShowManual(false)
            }, '技能已新增并启用')}>
              新增技能
            </button>
          </div>
        </div>
      )}

      {error && <p className="error banner">操作失败：{error}</p>}
      {notice && <p className="ok">{notice}</p>}
      <p className="muted">
        文件夹根目录需含 SKILL.md（frontmatter 里写 name / description，正文为技能内容）；
        一次导入一个文件夹，导入后默认停用，同名会被拒绝。
      </p>

      <ul className="skill-list">
        {items.map((item) => (
          <li key={item.id} className={item.enabled ? 'skill-card enabled' : 'skill-card'}>
            <div className="skill-info">
              <div className="skill-title">
                <strong>{item.name}</strong>
                <span className={item.enabled ? 'state-tag on' : 'state-tag off'}>
                  {item.enabled ? '已启用' : '已停用'}
                </span>
              </div>
              {item.description && <p className="muted skill-description">{item.description}</p>}
            </div>
            <div className="row-actions">
              <button type="button" className={item.enabled ? 'switch on' : 'switch'}
                aria-label={item.enabled ? `停用 ${item.name}` : `启用 ${item.name}`}
                aria-pressed={item.enabled}
                onClick={() => run(() => setSkillEnabled(item.id, !item.enabled),
                  item.enabled ? `已停用「${item.name}」` : `已启用「${item.name}」`)}>
                <span className="switch-knob" />
              </button>
              <RowActionMenu
                label={`技能操作：${item.name}`}
                items={[{ key: 'delete', label: '删除技能', danger: true,
                  onSelect: () => run(() => deleteSkill(item.id), '已删除') }]}
              />
            </div>
          </li>
        ))}
        {items.length === 0 && <li className="muted">暂无技能，可从文件夹导入或手动新增。</li>}
      </ul>
    </section>
  )
}
