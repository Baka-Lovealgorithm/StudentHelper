import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

export interface RowMenuItem {
  key: string
  label: string
  danger?: boolean
  disabled?: boolean
  onSelect: () => void
}

interface Props {
  items: RowMenuItem[]
  /** 触发按钮的无障碍说明，如「会话操作」「技能操作」。 */
  label: string
  /** 触发按钮内容；默认「⋯」。 */
  trigger?: ReactNode
  disabled?: boolean
}

/**
 * CR-034：列表行统一编辑入口。悬停 / 聚焦显示触发按钮，点击弹出「重命名 / 删除」等
 * 菜单项；菜单 portal 到 body、fixed 定位，避免被 `.sidebar-sessions` 等滚动容器裁剪。
 * 点外部、Esc、列表滚动、窗口缩放时关闭；菜单项点击后先关闭再执行。
 */
export default function RowActionMenu({ items, label, trigger, disabled = false }: Props) {
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState<{ left: number; top: number } | null>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  useLayoutEffect(() => {
    if (!open) return
    const rect = triggerRef.current?.getBoundingClientRect()
    if (!rect) return
    const menuH = menuRef.current?.offsetHeight ?? items.length * 34 + 12
    const left = Math.max(8, Math.min(rect.right, window.innerWidth - 8) - 168)
    const top = rect.bottom + 6 + menuH > window.innerHeight - 8
      ? Math.max(8, rect.top - menuH - 6)
      : rect.bottom + 6
    setPos({ left, top })
  }, [open, items.length])

  useEffect(() => {
    if (!open) return
    const close = () => setOpen(false)
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.stopPropagation(); setOpen(false) }
    }
    const onPointer = (event: MouseEvent) => {
      const target = event.target as Node
      if (triggerRef.current?.contains(target) || menuRef.current?.contains(target)) return
      setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    window.addEventListener('mousedown', onPointer)
    window.addEventListener('resize', close)
    // scroll 用捕获阶段：会话列表 / 卡片容器内部滚动也会关闭菜单
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
        className={`row-menu-trigger ${open ? 'open' : ''}`}
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={disabled}
        onClick={(e) => { e.stopPropagation(); setOpen((previous) => !previous) }}
      >
        {trigger ?? <span aria-hidden="true">⋯</span>}
      </button>
      {open && pos && createPortal(
        <div ref={menuRef} className="row-menu" role="menu" aria-label={label}
          style={{ left: pos.left, top: pos.top }}>
          {items.map((item) => (
            <button
              key={item.key}
              type="button"
              role="menuitem"
              className={`row-menu-item ${item.danger ? 'danger' : ''}`}
              disabled={item.disabled}
              onClick={(e) => {
                e.stopPropagation()
                setOpen(false)
                item.onSelect()
              }}
            >
              {item.label}
            </button>
          ))}
        </div>,
        document.body,
      )}
    </>
  )
}
