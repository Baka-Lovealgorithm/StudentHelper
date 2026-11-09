import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'

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

  const pick = (event: ChangeEvent<HTMLInputElement>) => {
    const picked = Array.from(event.target.files ?? [])
    setLocalError(null)
    if (picked.length + files.length > MAX_FILES) {
      setLocalError(`单条消息最多 ${MAX_FILES} 张图片`)
      return
    }
    for (const file of picked) {
      if (!ALLOWED.includes(file.type)) {
        setLocalError('仅支持 png / jpg / webp 图片')
        return
      }
      if (file.size > MAX_BYTES) {
        setLocalError('单张图片不能超过 10 MB')
        return
      }
    }
    setFiles((previous) => [...previous, ...picked])
    if (inputRef.current) inputRef.current.value = ''
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
    <form className="composer" onSubmit={submit}>
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
        placeholder="输入数据结构题目，例如：已知二叉树的前序与中序序列，说明如何唯一确定该树……"
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
