import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  createKnowledge, deleteKnowledge, listKnowledgePage, listKnowledgeSubjects,
  type KnowledgePoint,
} from '../api/knowledge'
import FormDialog from './FormDialog'
import ConfirmDialog from './ConfirmDialog'

/**
 * T-203 / CR-040：知识点面板。
 *
 * <p>只读列表升级为管理面板：学科 + 创建日期区间筛选、手动新增、行内删除。
 * 掌握度按用户裁决退役，列表只展示学科归属、命中次数与创建时间。
 * 列表按每页 {@link PAGE_SIZE} 条分批展示，底部「更多」加载下一批。
 * CR-067：手动新增由常驻添加栏改为「新增知识点」按钮 + 弹窗。</p>
 */
/** 每页条数：按用户要求固定 10 条，多的走「更多」。 */
const PAGE_SIZE = 10

const EMPTY_DRAFT = { subject: '', parentName: '', name: '' }

export default function KnowledgePanel() {
  const [rows, setRows] = useState<KnowledgePoint[]>([])
  const [total, setTotal] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [page, setPage] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [subjects, setSubjects] = useState<string[]>([])
  const [subject, setSubject] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [draft, setDraft] = useState(EMPTY_DRAFT)
  const [formOpen, setFormOpen] = useState(false)
  /** CR-069：待删除的知识点（行内删除先弹居中确认框，替代原生 confirm）。 */
  const [deleteTarget, setDeleteTarget] = useState<KnowledgePoint | null>(null)

  /**
   * 重新取第 0 页并替换列表。筛选变化、新增、删除后都走这里——
   * OFFSET 分页在数据增删后会错位，回第一页最稳。
   */
  const refresh = useCallback(async () => {
    const result = await listKnowledgePage({
      subject: subject || undefined,
      from: from || undefined,
      to: to || undefined,
    }, 0, PAGE_SIZE)
    setRows(result.items)
    setTotal(result.total)
    setHasMore(result.hasMore)
    setPage(0)
  }, [subject, from, to])

  /** 「更多」：追加下一批，按 id 去重，避免刷新后重复。 */
  const loadMore = async () => {
    if (loadingMore || !hasMore) return
    setLoadingMore(true)
    try {
      const next = page + 1
      const result = await listKnowledgePage({
        subject: subject || undefined,
        from: from || undefined,
        to: to || undefined,
      }, next, PAGE_SIZE)
      setRows((previous) => {
        const seen = new Set(previous.map((row) => row.id))
        return [...previous, ...result.items.filter((row) => !seen.has(row.id))]
      })
      setTotal(result.total)
      setHasMore(result.hasMore)
      setPage(next)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoadingMore(false)
    }
  }

  useEffect(() => {
    void refresh().catch((e) => setError(String(e)))
  }, [refresh])

  // CR-025：学科下拉来自后端去重清单，不再硬编码
  useEffect(() => {
    void listKnowledgeSubjects()
      .then(setSubjects)
      .catch(() => setSubjects([]))
  }, [])

  /** 打开新增弹窗：学科默认带入当前筛选学科，省一次输入。 */
  const openForm = () => {
    setDraft({ ...EMPTY_DRAFT, subject })
    setError(null)
    setNotice(null)
    setFormOpen(true)
  }

  const closeForm = () => {
    setFormOpen(false)
    setDraft(EMPTY_DRAFT)
  }

  /** 学科与小知识点必填：为空时按钮禁用，不把空值请求打到后端。 */
  const canSubmit = draft.subject.trim().length > 0 && draft.name.trim().length > 0

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit || busy) return
    setError(null)
    setNotice(null)
    setBusy(true)
    try {
      const created = await createKnowledge({
        subject: draft.subject.trim(),
        parentName: draft.parentName.trim(),
        name: draft.name.trim(),
      })
      closeForm()
      await refresh()
      void listKnowledgeSubjects().then(setSubjects).catch(() => undefined)
      setNotice(`已新增「${created.name}」，命中次数从 0 开始累计`)
    } catch (e) {
      setError(String(e))
    } finally {
      setBusy(false)
    }
  }

  /** CR-069：行内删除只记目标并弹居中确认框（原来是原生 confirm）。 */
  const handleDelete = (row: KnowledgePoint) => {
    setDeleteTarget(row)
  }

  const confirmDelete = async () => {
    const row = deleteTarget
    if (!row) return
    setDeleteTarget(null)
    const label = row.parentName ? `${row.parentName} > ${row.name}` : row.name
    setError(null)
    setNotice(null)
    try {
      await deleteKnowledge(row.id)
      await refresh()
      setNotice(`已删除「${label}」`)
    } catch (e) {
      setError(String(e))
    }
  }

  const resetFilters = () => {
    setSubject('')
    setFrom('')
    setTo('')
  }

  return (
    <section className="card">
      {/* CR-067：与学科规范 / 技能页同构——标题与说明在左、新增入口在右上角，表单一律进弹窗。 */}
      <div className="panel-head">
        <div>
          <h2>知识点回顾</h2>
          <p className="muted">
            由模型在每轮解答的第 2 段直接给出（每轮最多 3 个，格式为「大知识点 &gt; 小知识点」），
            按会话学科与大 / 小两级聚合；也可以手动补充要跟踪的知识点。日期筛选按「创建日期」的起止区间生效，两端都可以留空。
          </p>
        </div>
        <div className="row-actions panel-head-actions">
          <button type="button" className="btn primary" onClick={openForm}>新增知识点</button>
        </div>
      </div>

      <div className="knowledge-filter">
        <label>
          学科
          <select value={subject} onChange={(e) => setSubject(e.target.value)}>
            <option value="">全部</option>
            {subjects.map((item) => (
              <option key={item} value={item}>{item}</option>
            ))}
          </select>
        </label>
        <label>
          创建日期
          <input type="date" value={from} max={to || undefined}
            onChange={(e) => setFrom(e.target.value)} aria-label="起始日期" />
          <span className="muted">至</span>
          <input type="date" value={to} min={from || undefined}
            onChange={(e) => setTo(e.target.value)} aria-label="结束日期" />
        </label>
        <button type="button" className="btn small" onClick={() => void refresh()}>刷新</button>
        <button type="button" className="btn small ghost" onClick={resetFilters}>清空条件</button>
        <span className="muted">已显示 {rows.length} / 共 {total} 个知识点</span>
      </div>

      {error && !formOpen && <p className="error banner">操作失败：{error}</p>}
      {notice && <p className="muted">{notice}</p>}
      {rows.length === 0 ? (
        <p className="muted">当前条件下暂无知识点。可点右上角「新增知识点」手动添加，或清空筛选条件后查看全部。</p>
      ) : (
        <>
          <div className="knowledge-list">
            {rows.map((row) => (
              <div key={row.id} className="knowledge-row">
                <span className="kp-major">{row.parentName || '（单级）'}</span>
                <span className="kp-arrow">→</span>
                <span className="kp-name">{row.name}</span>
                <span className="kp-hit">命中 {row.hitCount} 次</span>
                <span className="kp-date">{row.createdAt ?? '—'}</span>
                <button type="button" className="btn small danger"
                  onClick={() => handleDelete(row)}>删除</button>
              </div>
            ))}
          </div>
          {hasMore && (
            <button type="button" className="btn load-more" onClick={() => void loadMore()}
              disabled={loadingMore}>
              {loadingMore ? '加载中…' : `更多（还有 ${Math.max(total - rows.length, 0)} 条）`}
            </button>
          )}
        </>
      )}

      <FormDialog
        open={formOpen}
        title="新增知识点"
        onClose={closeForm}
        footer={(
          <>
            <button type="button" className="btn" onClick={closeForm}>取消</button>
            <button type="submit" form="knowledge-create-form" className="btn primary"
              disabled={!canSubmit || busy}>
              {busy ? '添加中…' : '添加'}
            </button>
          </>
        )}
      >
        <form id="knowledge-create-form" onSubmit={(e) => void handleCreate(e)}>
          {error && <p className="error banner">操作失败：{error}</p>}
          <label className="field">学科
            <input value={draft.subject} list="knowledge-subject-options" placeholder="如：数据结构"
              onChange={(e) => setDraft({ ...draft, subject: e.target.value })} />
            <datalist id="knowledge-subject-options">
              {subjects.map((item) => <option key={item} value={item} />)}
            </datalist>
          </label>
          <label className="field">大知识点（可空）
            <input value={draft.parentName} placeholder="如：栈"
              onChange={(e) => setDraft({ ...draft, parentName: e.target.value })} />
          </label>
          <label className="field">小知识点
            <input value={draft.name} placeholder="如：栈顶指针 top"
              onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
          </label>
        </form>
      </FormDialog>

      {/* CR-069：删除知识点改用页面内居中确认框 */}
      <ConfirmDialog
        open={deleteTarget !== null}
        title="删除知识点"
        message={`确定删除知识点「${deleteTarget
          ? (deleteTarget.parentName ? `${deleteTarget.parentName} > ${deleteTarget.name}` : deleteTarget.name)
          : ''}」？它的命中明细会一并删除，且不可恢复。`}
        confirmText="删除"
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleteTarget(null)}
      />
    </section>
  )
}
