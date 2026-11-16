import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface Props {
  /** 触发既有隐藏 file input；校验与 chip 插入仍在 Composer 内。 */
  onPickFiles: () => void
  /** 技能二级面板内容（embedded 模式的 SessionSkillSelector）。 */
  skillPanel?: ReactNode
  disabled?: boolean
}

interface Pos {
  left: number
  top: number
  /** 右侧空间不足时二级面板翻到菜单左侧。 */
  flip: boolean
}

const MENU_W = 196
const MENU_H = 96
const SUBMENU_W = 262

/**
 * CR-051：输入区「+」菜单。参考 WorkBuddy 的一级菜单 + 二级面板：
 * 「上传图片」直接触发文件选择，「技能」展开会话级技能列表（勾选即写库）。
 * 菜单 portal 到 body、fixed 定位并向上展开，避免被输入卡片 / 消息滚动容器裁剪。
 * D-16：一级菜单项 hover 互斥——悬浮「上传图片」收起技能二级面板，悬浮 / 点击「技能」展开；
 * 二级面板自身保活，鼠标跨 6px 间隙进入面板时不消失。
 */
export default function ComposerPlusMenu({ onPickFiles, skillPanel, disabled = false }: Props) {
  const [open, setOpen] = useState(false)
  const [subOpen, setSubOpen] = useState(false)
  const [pos, setPos] = useState<Pos | null>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  useLayoutEffect(() => {
    if (!open) return
    const rect = triggerRef.current?.getBoundingClientRect()
    if (!rect) return
    const left = Math.max(8, Math.min(rect.left, window.innerWidth - MENU_W - 8))
    const top = rect.top - MENU_H - 8 > 8
      ? rect.top - MENU_H - 8
      : Math.min(rect.bottom + 8, window.innerHeight - MENU_H - 8)
    setPos({ left, top, flip: left + MENU_W + 6 + SUBMENU_W > window.innerWidth - 8 })
  }, [open])

  useEffect(() => {
    if (!open) return
    const close = () => { setOpen(false); setSubOpen(false) }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.stopPropagation(); close() }
    }
    const onPointer = (event: MouseEvent) => {
      const target = event.target as Node
      if (triggerRef.current?.contains(target) || menuRef.current?.contains(target)) return
      close()
    }
    window.addEventListener('keydown', onKey)
    window.addEventListener('mousedown', onPointer)
    window.addEventListener('resize', close)
    window.addEventListener('scroll', close, true)
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('mousedown', onPointer)
      window.removeEventListener('resize', close)
      window.removeEventListener('scroll', close, true)
    }
  }, [open])

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className={`composer-icon-btn plus-trigger ${open ? 'open' : ''}`}
        aria-label="添加图片或选择技能"
        title="添加图片或选择技能"
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={disabled}
        onClick={(event) => {
          event.stopPropagation()
          if (open) { setOpen(false); setSubOpen(false) } else { setOpen(true); setSubOpen(false) }
        }}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M12 5v14" /><path d="M5 12h14" />
        </svg>
      </button>
      {open && pos && createPortal(
        <div ref={menuRef} className="composer-plus-menu" role="menu" aria-label="添加内容"
          style={{ left: pos.left, top: pos.top }}>
          <button type="button" role="menuitem" className="composer-plus-item"
            onMouseEnter={() => setSubOpen(false)}
            onClick={(event) => { event.stopPropagation(); setOpen(false); setSubOpen(false); onPickFiles() }}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
              strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="m21.4 11.6-8.9 8.9a5 5 0 0 1-7.1-7.1l9-9a3.5 3.5 0 0 1 5 5l-9 9a2 2 0 0 1-2.8-2.8l8.3-8.3" />
            </svg>
            <span>上传图片</span>
          </button>
          <button type="button" role="menuitem" className={`composer-plus-item ${subOpen ? 'active' : ''}`}
            aria-haspopup="menu" aria-expanded={subOpen}
            onMouseEnter={() => setSubOpen(true)}
            onClick={(event) => { event.stopPropagation(); setSubOpen(true) }}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
              strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M12 2 4 6v6c0 5 3.4 8.7 8 10 4.6-1.3 8-5 8-10V6l-8-4Z" />
              <path d="m9 12 2 2 4-4" />
            </svg>
            <span>技能</span>
            <span className="caret" aria-hidden="true">›</span>
          </button>
          {subOpen && (
            <div className={`composer-plus-submenu ${pos.flip ? 'flip' : ''}`} role="menu"
              aria-label="选择本会话技能"
              onMouseEnter={() => setSubOpen(true)}>
              {skillPanel ?? <div className="model-empty">技能功能未开启</div>}
            </div>
          )}
        </div>,
        document.body,
      )}
    </>
  )
}
