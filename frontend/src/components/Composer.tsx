import { useEffect, useRef, useState, type ChangeEvent, type ClipboardEvent, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
import GuideModeSelector from './GuideModeSelector'
import ComposerPlusMenu from './ComposerPlusMenu'

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
  /** CR-036：引导模式改由输入区下拉承载，切换仍走会话级 setGuideMode。 */
  onGuideModeChange: (mode: number) => void
  onSubmit: (text: string, hint: string, images: File[], parts: ComposerPart[]) => void
  /** CR-013 / CR-014：由上层注入的模型选择器，渲染在发送按钮左侧。 */
  modelSlot?: ReactNode
  /** CR-018：会话级学科三态选择器，渲染在输入框底部工具条最左侧。 */
  subjectSlot?: ReactNode
  /** CR-038：会话级通用技能选择器，与引导模式 / 模型选择器同列于工具条右侧。 */
  skillSlot?: ReactNode
  /** CR-053：生成中点击停止当前回答。 */
  onStop?: () => void
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

export default function Composer({ disabled, busy, guideMode, onGuideModeChange, onSubmit, modelSlot, subjectSlot, skillSlot, onStop }: Props) {
  const [localError, setLocalError] = useState<string | null>(null)
  const editorRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const imagesRef = useRef(new Map<string, File>())
  const urlsRef = useRef<string[]>([])
  /** CR-014：图片 chip 的统一编号文案，删除不复用编号。 */
  const imageCounterRef = useRef(0)
  /** 悬停预览挂在 body 上，避免被编辑区的 overflow 裁剪；提交 / 卸载时统一清理。 */
  const previewsRef = useRef<HTMLElement[]>([])

  useEffect(() => () => {
    urlsRef.current.forEach((url) => URL.revokeObjectURL(url))
    previewsRef.current.forEach((node) => node.remove())
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

  /** 在光标处插入图片 chip；位置即提交时的图文顺序。CR-014 起默认只显示统一文案 + 图标。 */
  const insertChip = (file: File) => {
    const editor = editorRef.current
    if (!editor) return
    const id = `img-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const label = `图片 ${++imageCounterRef.current}`
    imagesRef.current.set(id, file)
    const url = URL.createObjectURL(file)
    urlsRef.current.push(url)

    const chip = document.createElement('span')
    chip.className = 'img-chip'
    chip.contentEditable = 'false'
    chip.dataset.imgId = id
    chip.tabIndex = 0
    chip.setAttribute('aria-label', label)

    const icon = document.createElement('span')
    icon.className = 'chip-icon'
    icon.setAttribute('aria-hidden', 'true')
    icon.textContent = '🖼'
    const text = document.createElement('span')
    text.className = 'chip-label'
    text.textContent = label

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
      preview.remove()
      previewsRef.current = previewsRef.current.filter((node) => node !== preview)
      URL.revokeObjectURL(url)
    })

    // 悬停 / 键盘聚焦才展示具体内容：缩略图 + 原始文件名与大小。
    // 挂到 body 并用 fixed 定位，避免被 .composer-editor 的 overflow-y 裁剪。
    const preview = document.createElement('span')
    preview.className = 'chip-preview'
    const thumb = document.createElement('img')
    thumb.src = url
    thumb.alt = file.name || label
    const meta = document.createElement('span')
    meta.className = 'chip-meta'
    meta.textContent = `${file.name || '粘贴的图片'} · ${Math.max(1, Math.round(file.size / 1024))} KB`
    preview.append(thumb, meta)
    document.body.appendChild(preview)
    previewsRef.current.push(preview)
    chip.title = meta.textContent

    const showPreview = () => {
      const rect = chip.getBoundingClientRect()
      const height = preview.offsetHeight || 190
      preview.style.left = `${Math.max(8, Math.min(rect.left, window.innerWidth - 210))}px`
      preview.style.top = `${rect.top - height - 8 > 8 ? rect.top - height - 8 : rect.bottom + 8}px`
      preview.classList.add('visible')
    }
    const hidePreview = () => preview.classList.remove('visible')
    chip.addEventListener('mouseenter', showPreview)
    chip.addEventListener('mouseleave', hidePreview)
    chip.addEventListener('focus', showPreview)
    chip.addEventListener('blur', hidePreview)

    chip.append(icon, text, remove)

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
    // CR-051：难度输入框已移除，hint 恒为空；字段 / 接口 / 落库 / 历史回显与重新生成沿用不变。
    onSubmit(text, '', images, images.length > 0 ? parts : [])
    if (editorRef.current) editorRef.current.innerHTML = ''
    imagesRef.current.clear()
    urlsRef.current.forEach((url) => URL.revokeObjectURL(url))
    urlsRef.current = []
    previewsRef.current.forEach((node) => node.remove())
    previewsRef.current = []
  }

  return (
    <form className="composer" onSubmit={submit}>
      {localError && <p className="error">{localError}</p>}
      <div className="composer-box">
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
        <div className="composer-toolbar">
          <div className="composer-toolbar-main">
            {/* CR-051：上传图片 + 技能收进单个「+」菜单；学科改图标按钮 + 设置浮层。 */}
            <ComposerPlusMenu
              onPickFiles={() => inputRef.current?.click()}
              skillPanel={skillSlot}
              disabled={disabled || busy}
            />
            {subjectSlot}
            {/* 文件选择由加号菜单触发；隐藏 input 保留既有校验、chip 与提交链路。 */}
            <input ref={inputRef} type="file" hidden accept="image/png,image/jpeg,image/webp"
              multiple onChange={pick} disabled={disabled || busy} />
          </div>
          <div className="composer-toolbar-actions">
            <GuideModeSelector mode={guideMode} onChange={onGuideModeChange} disabled={busy} />
            {modelSlot}
            {/* CR-053：生成中把发送按钮换成可点击的「停止」，完成后自动恢复。 */}
            {busy ? (
              <button type="button" className="btn send-btn stop-btn"
                title="停止生成" aria-label="停止生成" disabled={!onStop}
                onClick={(event) => { event.preventDefault(); onStop?.() }}>
                <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <rect x="6" y="6" width="12" height="12" rx="2" />
                </svg>
                <span>停止</span>
              </button>
            ) : (
              <button type="submit" className="btn primary send-btn"
                title="发送内容" aria-label="发送内容" disabled={disabled}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                  strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M22 2 11 13" />
                  <path d="M22 2 15 22 11 13 2 9z" />
                </svg>
              </button>
            )}
          </div>
        </div>
      </div>
      {/* TCR-060：删除「切换仅影响当前会话，新会话继承全局默认模型」说明行。 */}
      {/* CR-034：AI 生成说明统一放在输入框下方，消息内不再显示。 */}
      <p className="composer-ai-note">内容由 AI 生成，请核实重要信息</p>
    </form>
  )
}
