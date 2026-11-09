import { lazy, Suspense, useEffect, useState } from 'react'
import { getUnlockState, unlockStep } from '../api/stepUnlock'

// Markdown / KaTeX 依赖较重，拆成独立 chunk，首屏只下载答题界面骨架（TCR-010）。
const MarkdownRenderer = lazy(() => import('./MarkdownRenderer'))

interface Props {
  content: string
  streaming?: boolean
  stepUnlockEnabled?: boolean
  messageId?: number
}

interface Section {
  title: string
  body: string
}

const MARKERS = ['①', '②', '③', '④', '⑤', '⑥', '⑦']

function splitSections(content: string): { prefix: string; sections: Section[] } {
  const lines = content.split('\n')
  let prefix = ''
  const sections: Section[] = []
  let current: Section | null = null
  for (const line of lines) {
    const trimmed = line.trim()
    if (trimmed.startsWith('## ') && MARKERS.some((m) => trimmed.slice(3).startsWith(m))) {
      if (current) sections.push(current)
      current = { title: trimmed.slice(3), body: '' }
    } else if (current) {
      current.body += (current.body ? '\n' : '') + line
    } else {
      prefix += (prefix ? '\n' : '') + line
    }
  }
  if (current) sections.push(current)
  return { prefix: prefix.trim(), sections }
}

/** 懒加载未就绪时先以纯文本展示，避免流式过程出现空白或布局跳动。 */
function Markdown({ text }: { text: string }) {
  return (
    <Suspense fallback={<pre className="solution-raw">{text}</pre>}>
      <MarkdownRenderer text={text} />
    </Suspense>
  )
}

export default function SolutionView({
  content, streaming = false, stepUnlockEnabled = false, messageId,
}: Props) {
  const { prefix, sections } = splitSections(content)
  const [unlocked, setUnlocked] = useState(3)

  useEffect(() => {
    if (!stepUnlockEnabled || streaming || !messageId) {
      setUnlocked(3)
      return
    }
    let active = true
    void getUnlockState(messageId)
      .then((state) => { if (active) setUnlocked(state.unlockedStep) })
      .catch(() => { if (active) setUnlocked(1) })
    return () => { active = false }
  }, [stepUnlockEnabled, streaming, messageId])

  const gated = stepUnlockEnabled && !streaming && sections.length > 3
  const visible = gated
    ? sections.filter((_, index) => index < 3 || (index === 3 ? unlocked >= 2 : unlocked >= 3))
    : sections
  const doUnlock = () => void (async () => {
    if (!messageId) return
    try {
      const state = await unlockStep(messageId, Math.min(3, unlocked + 1))
      setUnlocked(state.unlockedStep)
    } catch { /* 保持当前解锁状态 */ }
  })()

  return (
    <div className={streaming ? 'solution streaming' : 'solution'}>
      {prefix && <Markdown text={prefix} />}
      {visible.map((section) => (
        <section key={section.title} className="solution-section">
          <h4>{section.title}</h4>
          <Markdown text={section.body.trim()} />
          {section.title.startsWith('⑤') && (
            <button
              type="button"
              className="btn small"
              onClick={() => void navigator.clipboard.writeText(section.body.trim())}
            >
              复制答案
            </button>
          )}
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
      {content.includes('本内容由 AI 生成，请自行核对') && (
        <p className="disclaimer">本内容由 AI 生成，请自行核对</p>
      )}
      {streaming && <span className="cursor">▍</span>}
    </div>
  )
}
