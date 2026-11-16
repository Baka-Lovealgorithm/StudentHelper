import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface Props {
  open: boolean
  /** 标题，如「删除会话」。 */
  title: string
  /** 正文，可含会话标题等上下文。 */
  message: ReactNode
  confirmText?: string
  cancelText?: string
  /** 确认回调：由调用方决定弹窗何时关闭（先关再请求，避免请求期间残留遮罩）。 */
  onConfirm: () => void
  /** 取消：Esc / 点遮罩 / 点 × / 点取消四处统一走这里。 */
  onCancel: () => void
}

/**
 * CR-068：页面内居中确认弹窗，取代 `window.confirm`（浏览器原生框弹在窗口顶部、样式不可控）。
 *
 * <p>portal 到 body 避免被父容器裁切；样式复用 `form-dialog-*` 骨架，只把宽度收窄到 420px。
 * 打开时锁背景滚动、默认焦点落在「取消」，避免回车误触危险操作；关闭或卸载时恢复原值。</p>
 */
export default function ConfirmDialog({
  open, title, message, confirmText = '确定', cancelText = '取消', onConfirm, onCancel,
}: Props) {
  const cancelRef = useRef<HTMLButtonElement>(null)
  const cancelHandler = useRef(onCancel)
  cancelHandler.current = onCancel

  useEffect(() => {
    if (!open) return undefined
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') cancelHandler.current()
    }
    const previousOverflow = document.body.style.overflow
    document.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden'
    // 危险操作默认焦点给「取消」，回车不会直接删
    cancelRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [open])

  if (!open) return null

  return createPortal(
    <div
      className="form-dialog-backdrop"
      onMouseDown={(event) => { if (event.target === event.currentTarget) cancelHandler.current() }}
    >
      <div className="form-dialog confirm-dialog" role="alertdialog" aria-modal="true" aria-label={title}>
        <div className="form-dialog-head">
          <h3>{title}</h3>
          <button type="button" className="form-dialog-close" aria-label="关闭" onClick={() => cancelHandler.current()}>
            ×
          </button>
        </div>
        <div className="form-dialog-body">
          <p>{message}</p>
        </div>
        <div className="form-dialog-foot">
          <button type="button" className="btn" ref={cancelRef} onClick={() => cancelHandler.current()}>
            {cancelText}
          </button>
          <button type="button" className="btn danger" onClick={onConfirm}>{confirmText}</button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
