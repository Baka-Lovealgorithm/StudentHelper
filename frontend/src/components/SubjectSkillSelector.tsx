import { useEffect, useRef, useState } from 'react'
import { listSubjectSkills, type SubjectSkillItem } from '../api/subjectSkills'
import { setSessionSubject, type SessionItem } from '../api/session'

interface Props {
  sessionId: number | null
  mode: string
  subjectSkillId: number | null
  /** 学科规范开关；关闭时整个选择器不渲染，也不请求接口。 */
  enabled: boolean
  disabled: boolean
  onChanged: (session: SessionItem) => void
  onError: (message: string) => void
}

const MODE_LABELS: Record<string, string> = {
  AUTO: '学科：模型自选',
  MANUAL: '学科：指定',
  OFF: '学科：不使用',
}

/**
 * CR-018：会话级学科三态选择（模型自选 / 指定学科 / 不使用）。
 * CR-051：文字下拉改为**图标按钮 + 设置浮层**——状态点 / 高亮表达当前态，
 * 浮层内三态单选 + 「指定」时的启用学科列表；写库仍走 setSessionSubject。
 */
export default function SubjectSkillSelector({
  sessionId, mode, subjectSkillId, enabled, disabled, onChanged, onError,
}: Props) {
  const [skills, setSkills] = useState<SubjectSkillItem[]>([])
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    if (!enabled) {
      setSkills([])
      return
    }
    let active = true
    void listSubjectSkills()
      .then((rows) => { if (active) setSkills(rows.filter((item) => item.enabled)) })
      .catch(() => { if (active) setSkills([]) })
    return () => { active = false }
  }, [enabled])

  useEffect(() => {
    if (!open) return
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
  }, [open])

  const patch = (nextMode: string, id: number | null) => void (async () => {
    if (!sessionId) return
    try {
      onChanged(await setSessionSubject(sessionId, nextMode, id))
    } catch (e) {
      onError(String(e))
    }
  })()

  if (!enabled) return null

  const currentMode = MODE_LABELS[mode] ? mode : 'AUTO'
  const activeSkills = skills
  const label = `学科规范：${MODE_LABELS[currentMode]}，点击修改（只影响当前会话）`

  const chooseMode = (next: string) => {
    if (next === 'MANUAL') {
      const first = activeSkills[0]
      if (!first) {
        onError('没有启用的学科规范，请先在「学科规范」面板启用')
        return
      }
      patch('MANUAL', first.id)
    } else {
      patch(next, null)
    }
    setOpen(false)
  }

  return (
    <span className="subject-selector" ref={rootRef}>
      <button
        type="button"
        className={`composer-icon-btn subject-trigger mode-${currentMode}`}
        aria-label={label}
        title={label}
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={disabled || !sessionId}
        onClick={() => setOpen((previous) => !previous)}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v18H6.5A2.5 2.5 0 0 0 4 22V4.5Z" />
          <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
          <path d="M9 7h7" /><path d="M9 11h5" />
        </svg>
        <span className="subject-state" aria-hidden="true" />
      </button>
      {open && (
        <div className="model-menu subject-menu" role="menu" aria-label="学科规范设置">
          <div className="model-group-name">学科规范（仅当前会话）</div>
          {(['AUTO', 'MANUAL', 'OFF'] as const).map((key) => (
            <button key={key} type="button" role="menuitemradio"
              aria-checked={currentMode === key}
              className={`model-option ${currentMode === key ? 'selected' : ''}`}
              onClick={() => chooseMode(key)}>
              <span>{MODE_LABELS[key].replace('学科：', '')}</span>
              {currentMode === key && <span className="model-check" aria-hidden="true">✓</span>}
            </button>
          ))}
          {currentMode === 'MANUAL' && (
            <>
              <div className="skill-menu-divider" />
              <div className="model-group-name">指定学科</div>
              {activeSkills.map((item) => (
                <button key={item.id} type="button" role="menuitemradio"
                  aria-checked={subjectSkillId === item.id}
                  className={`model-option ${subjectSkillId === item.id ? 'selected' : ''}`}
                  onClick={() => patch('MANUAL', item.id)}>
                  <span>{item.name}</span>
                  {subjectSkillId === item.id && <span className="model-check" aria-hidden="true">✓</span>}
                </button>
              ))}
              {activeSkills.length === 0 && <div className="model-empty">没有启用的学科规范</div>}
            </>
          )}
        </div>
      )}
    </span>
  )
}
