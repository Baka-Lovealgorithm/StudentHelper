import { lazy, Suspense, useEffect, useState, type ReactNode } from 'react'
import { getUnlockState, unlockStep } from '../api/stepUnlock'
import { stripSessionTitle } from '../utils/sessionTitle'
import {
  composeAnswer, explainTitlesOf, isFreeText, isGated, splitDisclaimer, splitSections,
  visibleSections, type Section,
} from '../features/solution/sections'

// Markdown / KaTeX 依赖较重，拆成独立 chunk，首屏只下载答题界面骨架（TCR-010）。
const MarkdownRenderer = lazy(() => import('./MarkdownRenderer'))

interface Props {
  content: string
  streaming?: boolean
  stepUnlockEnabled?: boolean
  messageId?: number
  /** CR-018：该回答的输出结构；null 按默认六段，FREE_TEXT 为追问自由文本。 */
  sectionSchema?: string | null
  /** CR-039：本轮回答形态；历史消息为 null 时按 sectionSchema 推断。 */
  intent?: 'SOLVE' | 'FOLLOW_UP' | 'EXPLAIN' | null
  /** TCR-060：消息级操作（标记有误 / 重新生成 / 举一反三 / 加入错题本）与复制按钮同排。 */
  actions?: ReactNode
  /** CR-062：点答案里的图片时回调，由上层打开页内放大层。 */
  onZoomImage?: (url: string) => void
}

/** 懒加载未就绪时先以纯文本展示，避免流式过程出现空白或布局跳动。 */
function Markdown({ text, onZoomImage }: { text: string; onZoomImage?: (url: string) => void }) {
  return (
    <Suspense fallback={<pre className="solution-raw">{text}</pre>}>
      <MarkdownRenderer text={text} onZoomImage={onZoomImage} />
    </Suspense>
  )
}

/**
 * D-11：整条回答一个复制按钮，复制全部可见段落。
 * 分级解锁时只拼接已解锁段落，避免用复制绕过解锁。
 */
function CopyButton({ text, disabled }: { text: string; disabled: boolean }) {
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    const value = text.trim()
    if (!value) return
    try {
      await navigator.clipboard.writeText(value)
    } catch {
      const area = document.createElement('textarea')
      area.value = value
      area.setAttribute('readonly', '')
      area.style.position = 'fixed'
      area.style.top = '-1000px'
      area.style.opacity = '0'
      document.body.appendChild(area)
      area.select()
      try {
        document.execCommand('copy')
      } finally {
        document.body.removeChild(area)
      }
    }
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }

  return (
    <button
      type="button"
      className="icon-action"
      title={copied ? '已复制' : '复制答案'}
      aria-label={copied ? '已复制' : '复制答案'}
      onClick={() => void copy()}
      disabled={disabled || !text.trim()}
    >
      {copied ? (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M20 6 9 17l-5-5" />
        </svg>
      ) : (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <rect x="9" y="9" width="11" height="11" rx="2" />
          <path d="M5 15V5a2 2 0 0 1 2-2h10" />
        </svg>
      )}
    </button>
  )
}

export default function SolutionView({
  content, streaming = false, stepUnlockEnabled = false, messageId, sectionSchema = null,
  intent = null, actions = null, onZoomImage,
}: Props) {
  // CR-033：先剥离【会话标题】行（与后端 SessionTitleParser 同口径），
  // CR-034：免责声明不再在消息内渲染，但仍需剥离末尾提示行，避免正文重复。
  const { body } = splitDisclaimer(stripSessionTitle(content, streaming))
  const freeText = isFreeText(intent, sectionSchema)
  const isExplain = intent === 'EXPLAIN'
  const { prefix, sections } = freeText
    ? { prefix: body, sections: [] as Section[] }
    : splitSections(body, sectionSchema, isExplain ? explainTitlesOf(sectionSchema) : undefined)
  // CR-043：门控生效时首帧按最小可见（1 级），effect 回读服务端后再修正；
  // 改造前初值固定为 3，会先全放行再收起。
  const [unlocked, setUnlocked] = useState(() => (stepUnlockEnabled && !streaming ? 1 : 3))

  useEffect(() => {
    if (!stepUnlockEnabled || streaming || !messageId || freeText || isExplain) {
      setUnlocked(3)
      return
    }
    let active = true
    void getUnlockState(messageId)
      .then((state) => { if (active) setUnlocked(state.unlockedStep) })
      .catch(() => { if (active) setUnlocked(1) })
    return () => { active = false }
  }, [stepUnlockEnabled, streaming, messageId, freeText, isExplain])

  if (freeText) {
    return (
      <div className={streaming ? 'solution streaming' : 'solution'}>
        <Markdown text={body} onZoomImage={onZoomImage} />
        {/* TCR-060：复制与消息级操作放同一行，避免图标分成两排。 */}
        <div className="answer-actions">
          <CopyButton text={body} disabled={streaming} />
          {actions}
        </div>
        {/* CR-034：免责声明统一移到输入框下方，消息内不再渲染；splitDisclaimer 仍剥离落库提示行。 */}
        {streaming && <span className="cursor">▍</span>}
      </div>
    )
  }

  // 讲解不是题目解答，不做分步解锁
  const gated = isGated({ stepUnlockEnabled, streaming, isExplain, sectionCount: sections.length })
  const visible = visibleSections(sections, unlocked, gated)
  const doUnlock = () => void (async () => {
    if (!messageId) return
    try {
      const state = await unlockStep(messageId, Math.min(3, unlocked + 1))
      setUnlocked(state.unlockedStep)
    } catch { /* 保持当前解锁状态 */ }
  })()

  return (
    <div className={streaming ? 'solution streaming' : 'solution'}>
      {prefix && <Markdown text={prefix} onZoomImage={onZoomImage} />}
      {visible.map((section) => (
        <section key={section.title} className="solution-section">
          <h4>{section.title}</h4>
          <Markdown text={section.body.trim()} onZoomImage={onZoomImage} />
        </section>
      ))}
      {gated && (
        <div className="unlock-bar">
          <span className="muted">当前解锁：第 {unlocked} 级（1=思路 2=关键步骤 3=答案）</span>
          {unlocked < 3 && (
            <button type="button" className="btn small" onClick={doUnlock}>解锁下一步</button>
          )}
        </div>
      )}
      <div className="answer-actions">
        <CopyButton text={composeAnswer(prefix, visible)} disabled={streaming} />
        {actions}
      </div>
      {/* CR-034：免责声明统一移到输入框下方，消息内不再渲染。 */}
      {streaming && <span className="cursor">▍</span>}
    </div>
  )
}
