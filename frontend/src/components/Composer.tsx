import { useEffect, useRef, useState, type ChangeEvent, type ClipboardEvent, type FormEvent } from 'react'

interface Props {
  disabled: boolean
  busy: boolean
  guideMode: number
  onSubmit: (text: string, hint: string, images: File[]) => void
}

const ALLOWED = ['image/png', 'image/jpeg', 'image/webp']
const MAX_BYTES = 10 * 1024 * 1024
const MAX_FILES = 4

export default function Composer({ disabled, busy, guideMode, onSubmit }: Props) {
  const [text, setText] = useState('')
  const [hint, setHint] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [previews, setPreviews] = useState<string[]>([])
  const [localError, setLocalError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const urls = files.map((file) => URL.createObjectURL(file))
    setPreviews(urls)
    return () => urls.forEach((url) => URL.revokeObjectURL(url))
  }, [files])

  /** 选择文件与粘贴图片共用同一套校验与入列规则，保证两种入口行为一致。 */
  const accept = (incoming: File[]): boolean => {
    if (incoming.length === 0) return false
    setLocalError(null)
    if (incoming.length + files.length > MAX_FILES) {
      setLocalError(`单条消息最多 ${MAX_FILES} 张图片`)
      return false
    }
    for (const file of incoming) {
      if (!ALLOWED.includes(file.type)) {
        setLocalError('仅支持 png / jpg / webp 图片')
        return false
      }
      if (file.size > MAX_BYTES) {
        setLocalError('单张图片不能超过 10 MB')
        return false
      }
    }
    setFiles((previous) => [...previous, ...incoming])
    return true
  }

  const pick = (event: ChangeEvent<HTMLInputElement>) => {
    accept(Array.from(event.target.files ?? []))
    if (inputRef.current) inputRef.current.value = ''
  }

  /** 截图 / 复制图片后 Ctrl+V：取剪贴板里的图片文件，交给 accept 走同一套规则。 */
  const paste = (event: ClipboardEvent<HTMLFormElement>) => {
    if (disabled || busy) return
    const pasted: File[] = []
    for (const item of Array.from(event.clipboardData?.items ?? [])) {
      if (item.kind !== 'file') continue
      if (!item.type.startsWith('image/')) continue
      const file = item.getAsFile()
      if (file) pasted.push(file)
    }
    if (pasted.length === 0) return
    // 剪贴板带图片时拦掉默认粘贴，避免把图片 URL 或二进制噪声写进输入框
    event.preventDefault()
    accept(pasted)
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (disabled || busy) return
    if (!text.trim() && files.length === 0) {
      setLocalError('请输入题目文字或上传图片')
      return
    }
    setLocalError(null)
    onSubmit(text.trim(), hint.trim(), files)
    setText('')
    setHint('')
    setFiles([])
  }

  return (
    <form className="composer" onSubmit={submit} onPaste={paste}>
      {localError && <p className="error">{localError}</p>}
      {previews.length > 0 && (
        <div className="preview-row">
          {previews.map((url, i) => (
            <div key={url} className="preview-item">
              <img src={url} alt={files[i]?.name ?? '图片'} />
              <button type="button" title="移除" onClick={() => setFiles((prev) => prev.filter((_, index) => index !== i))}>×</button>
            </div>
          ))}
        </div>
      )}
      <textarea
        rows={3}
        placeholder="输入数据结构题目，或直接按 Ctrl+V 粘贴截图；例如：已知二叉树的前序与中序序列，说明如何唯一确定该树……"
        value={text}
        onChange={(e) => setText(e.target.value)}
        disabled={disabled || busy}
      />
      <div className="composer-row">
        <input ref={inputRef} type="file" accept="image/png,image/jpeg,image/webp" multiple onChange={pick} disabled={disabled || busy} />
        <input
          className="hint-input"
          placeholder="难度 / 讲解要求（可选），如：用初学者能懂的话"
          value={hint}
          onChange={(e) => setHint(e.target.value)}
          disabled={disabled || busy}
        />
        <span className="mode-tag">{guideMode === 1 ? '仅思路' : '完整解答'}</span>
        <button type="submit" className="btn primary" disabled={disabled || busy}>{busy ? '生成中…' : '提交题目'}</button>
      </div>
    </form>
  )
}
