import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface Props {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  footer: ReactNode
}

/**
 * CR-060：新增 / 编辑表单的通用弹窗（MCP Server、学科规范共用）。
 *
 * <p>portal 到 body 避免被父容器裁切；遮罩点击 / Esc / 右上角 × 均可关闭且不提交。
 * 打开时锁定背景滚动，关闭或卸载时恢复原值。</p>
 */
export default function FormDialog({ open, title, onClose, children, footer }: Props) {
  const closeRef = useRef(onClose)
  closeRef.current = onClose

  useEffect(() => {
    if (!open) return undefined
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeRef.current()
    }
    const previousOverflow = document.body.style.overflow
    document.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [open])

  if (!open) return null

  return createPortal(
    <div
      className="form-dialog-backdrop"
      onMouseDown={(event) => { if (event.target === event.currentTarget) closeRef.current() }}
    >
      <div className="form-dialog" role="dialog" aria-modal="true" aria-label={title}>
        <div className="form-dialog-head">
          <h3>{title}</h3>
          <button type="button" className="form-dialog-close" aria-label="关闭" onClick={() => closeRef.current()}>
            ×
          </button>
        </div>
        <div className="form-dialog-body">{children}</div>
        <div className="form-dialog-foot">{footer}</div>
      </div>
    </div>,
    document.body,
  )
}
