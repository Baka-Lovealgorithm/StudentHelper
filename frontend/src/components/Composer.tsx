import { useEffect, useRef, useState, type ChangeEvent, type ClipboardEvent, type FormEvent, type KeyboardEvent } from 'react'

/** 有序图文段：value 为文字内容，index 指向 images 数组下标。 */
export interface ComposerPart {
  type: 'text' | 'image'
  value?: string
  index?: number
}

interface Props {
  disabled: boolean
  busy: boolean
  guideMode: number
  onSubmit: (text: string, hint: string, images: File[], parts: ComposerPart[]) => void
}

const ALLOWED = ['image/png', 'image/jpeg', 'image/webp']
const MAX_BYTES = 10 * 1024 * 1024
const MAX_FILES = 4
const PLACEHOLDER = '输入题目，或直接粘贴截图（Ctrl+V）；例如：已知二叉树的前序与中序序列，说明如何唯一确定该树……'

/** 剪贴板里的 File 可能没有 type，按扩展名兜底；后端仍只认文件头。 */
function typeOf(file: File): string {
  if (file.type) return file.type
  const name = file.name.toLowerCase()
  if (name.endsWith('.png')) return 'image/png'
  if (name.endsWith('.jpg') || name.endsWith('.jpeg')) return 'image/jpeg'
  if (name.endsWith('.webp')) return 'image/webp'
  return ''
}

export default function Composer({ disabled, busy, guideMode, onSubmit }: Props) {
  const [hint, setHint] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)
  const editorRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const imagesRef = useRef(new Map<string, File>())
  const urlsRef = useRef<string[]>([])

  useEffect(() => () => {
    urlsRef.current.forEach((url) => URL.revokeObjectURL(url))
  }, [])

  /** 文件选择与粘贴共用：类型、大小、数量三重校验。 */
  const check = (file: File): boolean => {
    if (imagesRef.current.size >= MAX_FILES) {
      setLocalError(`单条消息最多 ${MAX_FILES} 张图片`)
      return false
    }
    if (!ALLOWED.includes(typeOf(file))) {
      setLocalError('仅支持 png / jpg / webp 图片')
      return false
    }
    if (file.size > MAX_BYTES) {
      setLocalError('单张图片不能超过 10 MB')
      return false
    }
    setLocalError(null)
    return true
  }

  /** 在光标处插入图片缩略图；位置即提交时的图文顺序。 */
  const insertChip = (file: File) => {
    const editor = editorRef.current
    if (!editor) return
    const id = `img-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    imagesRef.current.set(id, file)
    const url = URL.createObjectURL(file)
    urlsRef.current.push(url)

    const chip = document.createElement('span')
    chip.className = 'img-chip'
    chip.contentEditable = 'false'
    chip.dataset.imgId = id
    const thumb = document.createElement('img')
    thumb.src = url
    thumb.alt = file.name || '图片'
    const remove = document.createElement('button')
    remove.type = 'button'
    remove.className = 'chip-remove'
    remove.title = '移除'
    remove.textContent = '×'
    remove.addEventListener('click', (event) => {
      event.preventDefault()
      event.stopPropagation()
      chip.remove()
      imagesRef.current.delete(id)
      URL.revokeObjectURL(url)
    })
    chip.append(thumb, remove)

    const selection = window.getSelection()
    let range: Range | null = null
    if (selection && selection.rangeCount > 0) {
      const current = selection.getRangeAt(0)
      if (editor.contains(current.commonAncestorContainer)) range = current
    }
    if (!range) {
      range = document.createRange()
      range.selectNodeContents(editor)
      range.collapse(false)
    }
    range.deleteContents()
    range.insertNode(chip)
    // 零宽空格保证 chip 之后仍可继续输入
    const spacer = document.createTextNode('\u200B')
    chip.after(spacer)
    range.setStartAfter(spacer)
    range.collapse(true)
    selection?.removeAllRanges()
    selection?.addRange(range)
    editor.focus()
  }

  /** 按 DOM 顺序读取文字段与图片段，顺序即用户输入顺序。 */
  const readParts = () => {
    const parts: ComposerPart[] = []
    const images: File[] = []
    let buffer = ''

    const flush = () => {
      if (buffer.length === 0) return
      parts.push({ type: 'text', value: buffer })
      buffer = ''
    }

    const walk = (node: Node) => {
      if (node.nodeType === Node.TEXT_NODE) {
        buffer += node.nodeValue ?? ''
        return
      }
      if (node.nodeType !== Node.ELEMENT_NODE) return
      const element = node as HTMLElement
      const imgId = element.dataset.imgId
      if (imgId) {
        flush()
        const file = imagesRef.current.get(imgId)
        if (file) {
          parts.push({ type: 'image', index: images.length })
          images.push(file)
        }
        return
      }
      if (element.tagName === 'BR') {
        buffer += '\n'
        return
      }
      element.childNodes.forEach(walk)
    }

    editorRef.current?.childNodes.forEach(walk)
    flush()

    // 去掉占位用的零宽空格，并丢弃只有空白的文字段
    const cleaned: ComposerPart[] = []
    for (const part of parts) {
      if (part.type === 'text') {
        const value = (part.value ?? '').replace(/\u200B/g, '')
        if (value.trim() === '') continue
        cleaned.push({ type: 'text', value })
      } else {
        cleaned.push(part)
      }
    }
    const text = cleaned
      .filter((part) => part.type === 'text')
      .map((part) => part.value ?? '')
      .join('\n')
      .trim()
    return { text, images, parts: cleaned }
  }

  const pick = (event: ChangeEvent<HTMLInputElement>) => {
    const picked = Array.from(event.target.files ?? [])
    if (inputRef.current) inputRef.current.value = ''
    for (const file of picked) {
      if (!check(file)) return
      insertChip(file)
    }
  }

  /** 粘贴图片：插入到光标处；若剪贴板同时带正文（如从 Word 复制图文）先把正文插入。 */
  const paste = (event: ClipboardEvent<HTMLDivElement>) => {
    if (disabled || busy) return
    const pasted: File[] = []
    for (const item of Array.from(event.clipboardData?.items ?? [])) {
      if (item.kind !== 'file' || !item.type.startsWith('image/')) continue
      const file = item.getAsFile()
      if (file) pasted.push(file)
    }
    if (pasted.length === 0) return
    event.preventDefault()

    const plain = (event.clipboardData?.getData('text/plain') ?? '').trim()
    if (plain && !/^https?:\/\/\S+$/i.test(plain)) {
      document.execCommand('insertText', false, plain)
    }
    for (const file of pasted) {
      if (!check(file)) return
      insertChip(file)
    }
  }

  /** Enter 统一插入 <br>，避免浏览器用 <div> 包裹导致顺序读取复杂化。 */
  const keydown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      document.execCommand('insertLineBreak')
    }
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (disabled || busy) return
    const { text, images, parts } = readParts()
    if (!text && images.length === 0) {
      setLocalError('请输入题目文字或上传图片')
      return
    }
    setLocalError(null)
    onSubmit(text, hint.trim(), images, images.length > 0 ? parts : [])
    if (editorRef.current) editorRef.current.innerHTML = ''
    imagesRef.current.clear()
    urlsRef.current.forEach((url) => URL.revokeObjectURL(url))
    urlsRef.current = []
    setHint('')
  }

  return (
    <form className="composer" onSubmit={submit}>
      {localError && <p className="error">{localError}</p>}
      <div
        ref={editorRef}
        className="composer-editor"
        contentEditable={!disabled && !busy}
        role="textbox"
        aria-multiline="true"
        aria-label="题目输入框，支持粘贴截图"
        data-placeholder={PLACEHOLDER}
        onPaste={paste}
        onKeyDown={keydown}
        suppressContentEditableWarning
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
