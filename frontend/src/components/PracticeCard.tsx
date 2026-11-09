import { useEffect, useState } from 'react'
import { generatePractice, submitAndJudge, type Practice } from '../api/practice'

interface Props {
  messageId: number
}

/** T-205 举一反三：生成相似题（不含答案）→ 作答 → 判分，状态按 PENDING / PASS / FAIL 展示。 */
export default function PracticeCard({ messageId }: Props) {
  const [practice, setPractice] = useState<Practice | null>(null)
  const [answer, setAnswer] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let alive = true
    setPractice(null)
    setAnswer('')
    setError(null)
    setBusy(true)
    void generatePractice(messageId)
      .then((p) => { if (alive) setPractice(p) })
      .catch((e) => { if (alive) setError(String(e)) })
      .finally(() => { if (alive) setBusy(false) })
    return () => { alive = false }
  }, [messageId])

  const submit = () => void (async () => {
    if (!practice || !answer.trim()) return
    setBusy(true)
    setError(null)
    try {
      setPractice(await submitAndJudge(practice.id, answer.trim()))
    } catch (e) {
      setError(String(e))
    } finally {
      setBusy(false)
    }
  })()

  return (
    <div className="practice-card">
      <h4>举一反三 · 相似题练习</h4>
      {busy && !practice && <p className="muted">正在生成相似题…</p>}
      {error && <p className="error">练习失败：{error}</p>}
      {practice && (
        <>
          <p className="practice-question">{practice.variantText}</p>
          {practice.judgeResult === 'PENDING' ? (
            <>
              <textarea
                value={answer}
                onChange={(e) => setAnswer(e.target.value)}
                placeholder="在此写下你的答案"
                rows={3}
              />
              <button type="button" className="btn small" disabled={busy || !answer.trim()} onClick={submit}>
                {busy ? '判分中…' : '提交并判分'}
              </button>
            </>
          ) : (
            <div className={`practice-result ${practice.judgeResult.toLowerCase()}`}>
              <strong>{practice.judgeResult === 'PASS' ? '通过' : '未通过'}</strong>
              <span>分数 {practice.score ?? '—'}</span>
              <p>{practice.feedback}</p>
            </div>
          )}
        </>
      )}
    </div>
  )
}
