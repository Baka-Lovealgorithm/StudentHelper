import { useCallback, useEffect, useRef, useState } from 'react'
import { listSkills, type SkillItem } from '../api/skills'
import { setSessionSkills, type SessionItem } from '../api/session'

interface Props {
  sessionId: number | null
  /** 当前会话固化的技能 id；空数组表示「自动」（模型按需读入）。 */
  skillIds: number[]
  /** 通用技能开关；关闭时整个选择器不渲染，也不请求接口。 */
  enabled: boolean
  disabled: boolean
  onChanged: (session: SessionItem) => void
  onError: (message: string) => void
  /** CR-051：加号菜单二级面板模式——只渲染选项列表，不渲染 trigger / 不自行管理开关。 */
  embedded?: boolean
}

const MAX_SKILLS = 2

/**
 * CR-038：输入框工具条里的会话级技能选择器。与模型 / 引导模式选择器同款 trigger + 列表，
 * 「自动」清除固化回到模型按需，其余为启用技能的多选（最多 2 条）；固化后每轮注入正文。
 * CR-051 起可作为加号菜单的二级面板（embedded），列表与写库逻辑完全复用。
 */
export default function SessionSkillSelector({
  sessionId, skillIds, enabled, disabled, onChanged, onError, embedded = false,
}: Props) {
  const [skills, setSkills] = useState<SkillItem[]>([])
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  const reload = useCallback(async () => {
    const rows = await listSkills()
    setSkills(rows.filter((item) => item.enabled))
  }, [])

  useEffect(() => {
    if (!enabled) {
      setSkills([])
      return
    }
    void reload().catch(() => setSkills([]))
  }, [enabled, reload])

  useEffect(() => {
    if (!open || embedded) return
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open, embedded])

  const patch = (next: number[]) => void (async () => {
    if (!sessionId) return
    try {
      onChanged(await setSessionSkills(sessionId, next))
    } catch (e) {
      onError(String(e))
    }
  })()

  const selected = skillIds ?? []
  const names = selected
    .map((id) => skills.find((item) => item.id === id)?.name)
    .filter((name): name is string => Boolean(name))
  const label = selected.length === 0
    ? '技能：自动'
    : `技能：${names.length > 0 ? names.join('、') : `${selected.length} 条固定`}`

  /** 两种形态共用的选项列表：写库、上限与选中标记只此一份。 */
  const options = (
    <>
      <button type="button" role="option" aria-selected={selected.length === 0}
        className={`model-option ${selected.length === 0 ? 'selected' : ''}`}
        onClick={() => { patch([]); setOpen(false) }}>
        <span>自动（模型按需读入）</span>
        {selected.length === 0 && <span className="model-check" aria-hidden="true">✓</span>}
      </button>
      <div className="skill-menu-divider" />
      <div className="model-group-name">固定技能（最多 {MAX_SKILLS} 条）</div>
      {skills.map((item) => {
        const active = selected.includes(item.id)
        return (
          <button type="button" role="option" aria-selected={active} key={item.id}
            className={`model-option ${active ? 'selected' : ''}`}
            onClick={() => {
              if (!active && selected.length >= MAX_SKILLS) {
                onError(`一个会话最多固定 ${MAX_SKILLS} 条技能，请先取消一条`)
                return
              }
              patch(active ? selected.filter((id) => id !== item.id) : [...selected, item.id])
            }}>
            <span>{item.name}</span>
            {active && <span className="model-check" aria-hidden="true">✓</span>}
          </button>
        )
      })}
      {skills.length === 0 && <div className="model-empty">没有启用的技能</div>}
    </>
  )

  if (embedded) {
    if (!enabled) return <div className="model-empty">技能功能未开启</div>
    return (
      <div className="model-menu skill-menu embedded" role="listbox" aria-label="选择本会话技能">
        {options}
      </div>
    )
  }

  if (!enabled) return null

  return (
    <div className="skill-selector" ref={rootRef}
      title="固化后的技能会在本会话每一轮生效（含追问）；「自动」回到模型按需读入">
      <button type="button" className="model-trigger" aria-haspopup="listbox" aria-expanded={open}
        aria-label="选择本会话技能" disabled={disabled || !sessionId}
        onClick={() => setOpen((previous) => !previous)}>
        <span className="model-trigger-label">{label}</span>
        <span className="model-caret" aria-hidden="true">⌄</span>
      </button>
      {open && (
        <div className="model-menu skill-menu" role="listbox" aria-label="选择本会话技能">
          {options}
        </div>
      )}
    </div>
  )
}
