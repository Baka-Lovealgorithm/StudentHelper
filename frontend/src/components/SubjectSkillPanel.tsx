import { useCallback, useEffect, useRef, useState, type ChangeEvent } from 'react'
import {
  createSubjectSkill, deleteSubjectSkill, importSubjectSkillFolder,
  listSubjectSkills, parseSections, parseSpecs, setSubjectSkillEnabled, updateSubjectSkill,
  type SubjectSkillItem,
} from '../api/subjectSkills'
import RowActionMenu from './RowActionMenu'
import FormDialog from './FormDialog'

const DEFAULT_SECTIONS = JSON.stringify({
  full: ['## ① 题目理解', '## ② 涉及知识点', '## ③ 解题思路',
    '## ④ 详细步骤', '## ⑤ 最终答案', '## ⑥ 易错点 / 验算'],
  idea: ['## ① 题目理解', '## ② 涉及知识点', '## ③ 解题思路'],
  explain: ['## ① 概念定义', '## ② 关键性质与适用条件', '## ③ 典型例子',
    '## ④ 与相邻概念的区别', '## ⑤ 常见误区'],
})

interface FormState {
  name: string
  description: string
  keywords: string
  /** CR-039：三段规范，留空表示回落系统默认。 */
  solve: string
  followUp: string
  explain: string
  sectionsJson: string
  enabled: boolean
}

const EMPTY_FORM: FormState = {
  name: '', description: '', keywords: '', solve: '', followUp: '', explain: '',
  sectionsJson: DEFAULT_SECTIONS, enabled: false,
}

/** 结构预览里的三段规范文本：某段为空时明确标注回落默认。 */
function formatSpecs(item: SubjectSkillItem): string {
  const specs = parseSpecs(item)
  return [
    `【解题】${specs.solve.trim() || '（留空，使用系统默认解题规范）'}`,
    `【追问】${specs.followUp.trim() || '（留空，使用系统默认追问规范）'}`,
    `【讲解】${specs.explain.trim() || '（留空，使用系统默认讲解规范）'}`,
  ].join('\n\n')
}

/** CR-018：学科规范面板（表单 CRUD、结构预览、SKILL.md 文件夹导入）；CR-060 取消内置只读、新增与编辑改弹窗。 */
export default function SubjectSkillPanel() {
  const [items, setItems] = useState<SubjectSkillItem[]>([])
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [expanded, setExpanded] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [importing, setImporting] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const folderRef = useRef<HTMLInputElement>(null)

  const refresh = useCallback(async () => {
    setItems(await listSubjectSkills())
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

  const resetForm = () => {
    setForm(EMPTY_FORM)
    setEditingId(null)
  }

  const submitForm = () => run(async () => {
    const payload = {
      name: form.name.trim(), description: form.description.trim(),
      keywords: form.keywords.trim(), specMd: form.solve.trim(),
      specJson: JSON.stringify({
        solve: form.solve.trim(), followUp: form.followUp.trim(), explain: form.explain.trim(),
      }),
      sectionsJson: form.sectionsJson.trim(), enabled: form.enabled,
    }
    if (editingId !== null) await updateSubjectSkill(editingId, payload)
    else await createSubjectSkill(payload)
    resetForm()
    setFormOpen(false)
  }, editingId !== null ? '学科规范已更新' : '学科规范已新增')

  const startEdit = (item: SubjectSkillItem) => {
    setEditingId(item.id)
    const specs = parseSpecs(item)
    setForm({
      name: item.name, description: item.description ?? '', keywords: item.keywords ?? '',
      solve: specs.solve, followUp: specs.followUp, explain: specs.explain,
      sectionsJson: item.sectionsJson, enabled: item.enabled,
    })
    setError(null)
    setNotice(null)
    setFormOpen(true)
  }

  const pickFolder = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    if (folderRef.current) folderRef.current.value = ''
    if (files.length === 0) return
    setImporting(true)
    const paths = files.map((file) =>
      (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name)
    const body = new FormData()
    files.forEach((file) => body.append('files', file))
    body.append('paths', JSON.stringify(paths))
    void (async () => {
      setError(null)
      setNotice(null)
      try {
        const created = await importSubjectSkillFolder(body)
        await refresh()
        setNotice(`已导入「${created.name}」，默认停用，可在列表中启用`)
      } catch (e) {
        setError(String(e))
      } finally {
        setImporting(false)
      }
    })()
  }

  return (
    <section className="card subject-skill-panel">
      {/* CR-064：与「技能」页同构——标题在左、操作按钮组在右上角；原生文件控件隐到按钮背后。 */}
      <div className="panel-head">
        <div>
          <h2>学科规范</h2>
          <p className="muted">
            学科知识全部由大模型提供；这里只配置「怎么答」的规范与输出结构。
            支持表单录入与 SKILL.md 文件夹导入，所有学科规范都可以编辑、停用或删除。
            与「技能」是两条独立链路，互不影响。
          </p>
        </div>
        <div className="row-actions panel-head-actions">
          <input
            ref={folderRef}
            type="file"
            multiple
            style={{ display: 'none' }}
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            {...{ webkitdirectory: '', directory: '' } as any}
            onChange={pickFolder}
            aria-label="选择学科规范文件夹"
          />
          <button type="button" className="btn"
            onClick={() => { resetForm(); setError(null); setNotice(null); setFormOpen(true) }}>
            新增学科规范
          </button>
          <button type="button" className="btn primary" disabled={importing}
            onClick={() => folderRef.current?.click()}>
            {importing ? '导入中…' : '从文件夹导入'}
          </button>
        </div>
      </div>

      {error && !formOpen && <p className="error banner">操作失败：{error}</p>}
      {notice && <p className="muted">{notice}</p>}

      <ul className="knowledge-list subject-skill-list">
        {items.map((item) => {
          const schema = parseSections(item.sectionsJson)
          return (
            <li key={item.id}>
              <div className="subject-skill-head">
                <strong>{item.name}</strong>
                <span className={`tag ${item.enabled ? 'on' : ''}`}>{item.enabled ? '已启用' : '已停用'}</span>
                {item.description && <p className="muted">{item.description}</p>}
                {item.keywords && <p className="muted">关键词：{item.keywords}</p>}
              </div>
              <div className="message-actions">
                <label className="switch-label">
                  <input
                    type="checkbox"
                    checked={item.enabled}
                    onChange={(e) => run(() => setSubjectSkillEnabled(item.id, e.target.checked),
                      `「${item.name}」已${e.target.checked ? '启用' : '停用'}`)}
                  />
                  启用
                </label>
                <button type="button" className="btn small"
                  onClick={() => setExpanded(expanded === item.id ? null : item.id)}>
                  {expanded === item.id ? '收起结构' : '查看结构'}
                </button>
                <RowActionMenu
                  label={`学科规范操作：${item.name}`}
                  items={[
                    { key: 'edit', label: '编辑规范', onSelect: () => startEdit(item) },
                    { key: 'delete', label: '删除规范', danger: true,
                      onSelect: () => run(() => deleteSubjectSkill(item.id), `已删除「${item.name}」`) },
                  ]}
                />
              </div>
              {expanded === item.id && (
                <div className="subject-schema-preview">
                  <p className="muted">完整解答（{schema?.full.length ?? 6} 段）</p>
                  <ol>{(schema?.full ?? []).map((title) => <li key={title}>{title}</li>)}</ol>
                  <p className="muted">仅思路（{schema?.idea.length ?? 3} 段）</p>
                  <ol>{(schema?.idea ?? []).map((title) => <li key={title}>{title}</li>)}</ol>
                  <p className="muted">知识点讲解（{schema?.explain.length ?? 5} 段）</p>
                  <ol>{(schema?.explain ?? []).map((title) => <li key={title}>{title}</li>)}</ol>
                  <details>
                    <summary>规范正文（解题 / 追问 / 讲解）</summary>
                    <pre>{formatSpecs(item)}</pre>
                  </details>
                </div>
              )}
            </li>
          )
        })}
        {items.length === 0 && <li className="muted">暂无学科规范</li>}
      </ul>

      <FormDialog
        open={formOpen}
        title={editingId !== null ? '编辑学科规范' : '新增学科规范'}
        onClose={() => { resetForm(); setFormOpen(false) }}
        footer={(
          <>
            <button type="button" className="btn" onClick={() => { resetForm(); setFormOpen(false) }}>取消</button>
            <button type="button" className="btn primary" onClick={submitForm}>
              {editingId !== null ? '保存修改' : '新增学科规范'}
            </button>
          </>
        )}
      >
        {error && <p className="error banner">操作失败：{error}</p>}
        <label className="field">名称
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="例如：线性代数" />
        </label>
        <label className="field">一句话描述
          <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })}
            placeholder="供模型自选时判断适用题目" />
        </label>
        <label className="field">关键词（顿号或逗号分隔）
          <input value={form.keywords} onChange={(e) => setForm({ ...form, keywords: e.target.value })}
            placeholder="矩阵、秩、特征值" />
        </label>
        <label className="field">解题规范（只写怎么答，不写教材知识）
          <textarea rows={6} value={form.solve}
            onChange={(e) => setForm({ ...form, solve: e.target.value })}
            placeholder="【记号与术语】…&#10;【推理要求】…&#10;【禁止】…" />
        </label>
        <label className="field">追问规范（留空则用系统默认追问规范）
          <textarea rows={4} value={form.followUp}
            onChange={(e) => setForm({ ...form, followUp: e.target.value })}
            placeholder="【回答标准】…&#10;【风格】…（留空即回落默认）" />
        </label>
        <label className="field">知识点讲解规范（留空则用系统默认讲解规范）
          <textarea rows={4} value={form.explain}
            onChange={(e) => setForm({ ...form, explain: e.target.value })}
            placeholder="【回答标准】…&#10;【依据】…（留空即回落默认）" />
        </label>
        <label className="field">输出结构 sections_json（full / idea / explain 三个标题数组，标题以 ## 开头）
          <textarea rows={6} value={form.sectionsJson}
            onChange={(e) => setForm({ ...form, sectionsJson: e.target.value })} />
        </label>
        <label className="switch-label">
          <input type="checkbox" checked={form.enabled}
            onChange={(e) => setForm({ ...form, enabled: e.target.checked })} />
          {editingId !== null ? '立即启用该规范' : '创建后立即启用'}
        </label>
      </FormDialog>
    </section>
  )
}
