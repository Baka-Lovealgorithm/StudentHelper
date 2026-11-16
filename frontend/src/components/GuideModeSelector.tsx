import { useEffect, useRef, useState } from 'react'

interface Props {
  /** 当前会话的引导模式：0 完整解答 / 1 仅思路。 */
  mode: number
  onChange: (mode: number) => void
  disabled?: boolean
}

const OPTIONS = [
  { value: 0, label: '完整解答' },
  { value: 1, label: '仅思路' },
]

/**
 * CR-036：引导模式从对话卡片右上角移入输入框工具条，形态与模型选择器一致
 * （trigger 显示当前值 + 向上展开的展示列表），setGuideMode 的会话级语义不变。
 */
export default function GuideModeSelector({ mode, onChange, disabled }: Props) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

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

  return (
    <div className="guide-selector" ref={rootRef} title="引导模式（切换仅影响当前会话）">
      <button type="button" className="model-trigger" aria-haspopup="listbox" aria-expanded={open}
        aria-label="选择引导模式" disabled={disabled} onClick={() => setOpen((previous) => !previous)}>
        <span className="model-trigger-label">{mode === 1 ? '仅思路' : '完整解答'}</span>
        <span className="model-caret" aria-hidden="true">⌄</span>
      </button>
      {open && (
        <div className="model-menu guide-menu" role="listbox" aria-label="选择引导模式">
          {OPTIONS.map((option) => (
            <button type="button" role="option" aria-selected={mode === option.value}
              className={`model-option ${mode === option.value ? 'selected' : ''}`}
              key={option.value}
              onClick={() => {
                onChange(option.value)
                setOpen(false)
              }}>
              <span>{option.label}</span>
              {mode === option.value && <span className="model-check" aria-hidden="true">✓</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
