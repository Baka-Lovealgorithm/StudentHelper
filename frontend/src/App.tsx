import { useCallback, useEffect, useState } from 'react'
import { fetchHealth, type HealthData } from './api/client'
import { submitQuestion } from './api/chat'
import {
  createSession, deleteSession, getSessionDetail, listSessions, setGuideMode,
  type SessionDetail, type SessionItem,
} from './api/session'
import SessionList from './components/SessionList'
import ModelConfigPanel from './components/ModelConfigPanel'
import UsagePanel from './components/UsagePanel'
import Composer from './components/Composer'
import SolutionView from './components/SolutionView'
import AgentStatusBar from './components/AgentStatusBar'
import { listFeedback, markFeedback } from './api/feedback'
import FeaturePanel from './components/FeaturePanel'
import { listFeatures } from './api/features'
import KnowledgePanel from './components/KnowledgePanel'
import PracticeCard from './components/PracticeCard'
import ProfilePanel from './components/ProfilePanel'
import WrongBookPanel from './components/WrongBookPanel'
import { markWrong } from './api/wrongBook'

const STORAGE_KEY = 'studenthelper.currentSessionId'

interface StreamEventData {
  stage?: string
  text?: string
  index?: number
  code?: string
  message?: string
  messageId?: number
  ttftMs?: number
  durationMs?: number
}

export default function App() {
  const [view, setView] = useState<'chat' | 'config' | 'usage' | 'features' | 'knowledge' | 'profile' | 'wrongBook'>('chat')
  /** 当前展开「举一反三」卡片的助手消息 id（T-205）。 */
  const [practiceFor, setPracticeFor] = useState<number | null>(null)
  /** S2 开关快照（DD-01），默认空对象即全部关闭。 */
  const [features, setFeatures] = useState<Record<string, boolean>>({})
  const [sessions, setSessions] = useState<SessionItem[]>([])
  const [currentId, setCurrentId] = useState<number | null>(() => {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? Number(raw) : null
  })
  const [detail, setDetail] = useState<SessionDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [health, setHealth] = useState<HealthData | null>(null)
  const [busy, setBusy] = useState(false)
  const [stage, setStage] = useState('')
  const [streamingText, setStreamingText] = useState('')
  const [streamError, setStreamError] = useState<string | null>(null)
  const [timing, setTiming] = useState<{ ttft: number; total: number } | null>(null)
  /** 已标记「有误」的消息 id（T-207），用于按钮去重与禁用。 */
  const [markedIds, setMarkedIds] = useState<Set<number>>(new Set())

  const refreshSessions = useCallback(async () => {
    setSessions(await listSessions())
  }, [])

  /** 稳定引用，避免 FeaturePanel 的 useEffect 因回调变化反复请求。 */
  const syncFeatures = useCallback((snapshot: Record<string, boolean>) => {
    setFeatures(snapshot)
    if (!snapshot.usagePanel) setView((v) => (v === 'usage' ? 'chat' : v))
    if (!snapshot.knowledgeExtract) setView((v) => (v === 'knowledge' ? 'chat' : v))
    if (!snapshot.memory) setView((v) => (v === 'profile' ? 'chat' : v))
    if (!snapshot.wrongBook) setView((v) => (v === 'wrongBook' ? 'chat' : v))
  }, [])

  const openSession = useCallback(async (id: number) => {
    const result = await getSessionDetail(id)
    setDetail(result)
    setCurrentId(id)
    localStorage.setItem(STORAGE_KEY, String(id))
    setView('chat')
    // S2 标记状态：开关关闭时不请求；失败不影响 S1 主链路
    try {
      const marks = features.feedback ? await listFeedback(id) : []
      setMarkedIds(new Set(marks.map((m) => m.messageId)))
    } catch {
      setMarkedIds(new Set())
    }
  }, [features.feedback])

  useEffect(() => {
    void (async () => {
      try {
        await refreshSessions()
        if (currentId) await openSession(currentId)
      } catch (e) {
        setError(String(e))
      }
    })()
    // 健康检查只驱动顶栏连接状态点，失败不当作业务错误弹出
    void fetchHealth().then((r) => setHealth(r.data)).catch(() => setHealth(null))
    // 首次加载 S2 开关快照；失败按全部关闭处理
    void listFeatures()
      .then((rows) => syncFeatures(Object.fromEntries(rows.map((r) => [r.key, r.enabled]))))
      .catch(() => setFeatures({}))
    // 首次挂载恢复上次会话
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const guard = async (action: () => Promise<void>) => {
    try {
      setError(null)
      await action()
    } catch (e) {
      setError(String(e))
    }
  }

  const handleCreate = () => void guard(async () => {
    const created = await createSession()
    await refreshSessions()
    await openSession(created.id)
  })

  const handleDelete = (id: number) => void guard(async () => {
    if (!window.confirm('确定删除该会话及其全部消息？')) return
    await deleteSession(id)
    if (currentId === id) {
      localStorage.removeItem(STORAGE_KEY)
      setCurrentId(null)
      setDetail(null)
    }
    await refreshSessions()
  })

  const handleGuideMode = (mode: number) => void guard(async () => {
    if (!currentId) return
    await setGuideMode(currentId, mode)
    await Promise.all([openSession(currentId), refreshSessions()])
  })

  const handleSubmit = (text: string, hint: string, images: File[]) => {
    void (async () => {
      setBusy(true)
      setStage('UNDERSTANDING')
      setStreamingText('')
      setStreamError(null)
      setError(null)
      setTiming(null)
      // 真实模型一次解题可产生数百个 delta，按 40ms 批量刷新，避免频繁 setState
      let buffer = ''
      let timer: number | null = null
      const flush = () => {
        if (timer !== null) { window.clearTimeout(timer); timer = null }
        if (buffer) { setStreamingText((prev) => prev + buffer); buffer = '' }
      }
      try {
        let sessionId = currentId
        if (!sessionId) {
          const created = await createSession()
          sessionId = created.id
          await openSession(sessionId)
          await refreshSessions()
        }
        await submitQuestion(
          { sessionId, text, hint, images, guideMode: detail?.session.guideMode ?? 0 },
          (event) => {
            let data: StreamEventData = {}
            try { data = JSON.parse(event.data) as StreamEventData } catch { /* 忽略非 JSON 事件 */ }
            if (event.event === 'status') setStage(data.stage ?? 'GENERATING')
            else if (event.event === 'delta') {
              buffer += data.text ?? ''
              if (timer === null) {
                timer = window.setTimeout(() => { timer = null; flush() }, 40)
              }
            }
            else if (event.event === 'done') {
              setStage('DONE')
              setTiming({ ttft: data.ttftMs ?? 0, total: data.durationMs ?? 0 })
            }
            else if (event.event === 'error') {
              setStreamError(`${data.code ?? 'E5000'}：${data.message ?? '生成失败'}`)
              setStage('FAILED')
            }
          },
        )
        flush()
        await openSession(sessionId)
        await refreshSessions()
      } catch (e) {
        flush()
        setError(String(e))
        setStage('FAILED')
      } finally {
        setBusy(false)
        setStreamingText('')
      }
    })()
  }

  /** 标记回答有误（FR-207 / T-207）：写入 t_message_feedback，同一消息同一类型幂等。 */
  const handleMarkIncorrect = (messageId: number) => void guard(async () => {
    await markFeedback(messageId, 'INCORRECT')
    setMarkedIds((prev) => new Set(prev).add(messageId))
  })

  /** 该助手回答前最近的一条用户题面是否带图；带图暂不支持重新生成（原图只在磁盘，没有 File 对象）。 */
  const questionHasImages = (index: number): boolean => {
    if (!detail) return false
    for (let i = index - 1; i >= 0; i -= 1) {
      const item = detail.messages[i]
      if (item.role === 'USER') return Boolean(item.imagePaths)
    }
    return false
  }

  /** 重新生成（AC-207-2）：沿用原题文字与难度要求再发一次，不覆盖原消息。 */
  const handleRegenerate = (index: number) => {
    if (!detail || busy) return
    for (let i = index - 1; i >= 0; i -= 1) {
      const item = detail.messages[i]
      if (item.role !== 'USER') continue
      if (item.imagePaths) {
        setError('图片题暂不支持重新生成，请重新上传图片后再提交')
        return
      }
      if (!item.content || !item.content.trim()) {
        setError('原题没有文字内容，无法重新生成')
        return
      }
      handleSubmit(item.content, item.hint ?? '', [])
      return
    }
    setError('未找到对应的用户题面，无法重新生成')
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <h1>大学生解题助手</h1>
          <span className="subtitle">S1 + S2 首批 · 解题主链路（数据结构）</span>
        </div>
        <nav>
          <span className={`conn ${health ? 'up' : 'down'}`} title={health ? `${health.app} ${health.status}` : '后端未连接'}>
            {health ? '后端已连接' : '后端未连接'}
          </span>
          <button type="button" className={view === 'chat' ? 'active' : ''} onClick={() => setView('chat')}>会话</button>
          {features.usagePanel && (
            <button type="button" className={view === 'usage' ? 'active' : ''} onClick={() => setView('usage')}>用量看板</button>
          )}
          <button type="button" className={view === 'config' ? 'active' : ''} onClick={() => setView('config')}>模型配置</button>
          {features.knowledgeExtract && (
            <button type="button" className={view === 'knowledge' ? 'active' : ''} onClick={() => setView('knowledge')}>知识点</button>
          )}
          {features.memory && (
            <button type="button" className={view === 'profile' ? 'active' : ''} onClick={() => setView('profile')}>记忆画像</button>
          )}
          {features.wrongBook && (
            <button type="button" className={view === 'wrongBook' ? 'active' : ''} onClick={() => setView('wrongBook')}>错题本</button>
          )}
          <button type="button" className={view === 'features' ? 'active' : ''} onClick={() => setView('features')}>功能开关</button>
        </nav>
      </header>

      <div className="layout">
        <aside className="sidebar">
          <SessionList
            sessions={sessions}
            currentId={currentId}
            onCreate={handleCreate}
            onSelect={(id) => void guard(() => openSession(id))}
            onDelete={handleDelete}
          />
        </aside>

        <main className="workspace">
          {error && <p className="error banner">操作失败：{error}</p>}

          {view === 'features' ? <FeaturePanel onChange={syncFeatures} />
            : view === 'profile' && features.memory ? <ProfilePanel />
            : view === 'wrongBook' && features.wrongBook
              ? <WrongBookPanel onOpenSession={(id) => void guard(() => openSession(id))} />
            : view === 'knowledge' && features.knowledgeExtract ? <KnowledgePanel />
            : view === 'usage' && features.usagePanel ? <UsagePanel />
            : view === 'config' ? <ModelConfigPanel /> : (
            <>
              <section className="card chat-card">
                <div className="chat-head">
                  <h2>{detail?.session.title ?? '未选择会话'}</h2>
                  {detail && (
                    <div className="guide-switch">
                      <span>引导模式</span>
                      <button type="button" disabled={detail.session.guideMode === 0} onClick={() => handleGuideMode(0)}>完整解答</button>
                      <button type="button" disabled={detail.session.guideMode === 1} onClick={() => handleGuideMode(1)}>仅思路</button>
                    </div>
                  )}
                </div>

                <AgentStatusBar stage={stage} busy={busy} error={streamError} timing={timing} />

                <div className="messages">
                  {detail && detail.messages.length > 0 ? detail.messages.map((message, index) => (
                    <div key={message.id} className={`message ${message.role.toLowerCase()}`}>
                      <span className="role">
                        {message.role === 'USER' ? '我' : '助手'} · {message.status}
                        {message.errorCode ? ` · ${message.errorCode}` : ''}
                      </span>
                      {message.role === 'USER' && message.hint && (
                        <p className="message-hint">难度 / 讲解要求：{message.hint}</p>
                      )}
                      {message.role === 'ASSISTANT'
                        ? (message.status === 'FAILED'
                            ? <details className="raw-answer">
                                <summary>生成结果格式异常，点击查看模型原始输出</summary>
                                <pre>{message.content ?? ''}</pre>
                              </details>
                            : <SolutionView content={message.content ?? ''} stepUnlockEnabled={features.stepUnlock} messageId={message.id} />)
                        : <p>{message.content ?? '（无文本内容）'}</p>}
                      {features.feedback && message.role === 'ASSISTANT' && message.status !== 'FAILED' && (
                        <div className="message-actions">
                          <button
                            type="button"
                            className="btn small"
                            disabled={busy || markedIds.has(message.id)}
                            onClick={() => handleMarkIncorrect(message.id)}
                          >
                            {markedIds.has(message.id) ? '已标记有误' : '标记有误'}
                          </button>
                          <button
                            type="button"
                            className="btn small"
                            disabled={busy || questionHasImages(index)}
                            title={questionHasImages(index) ? '图片题暂不支持重新生成' : '沿用本题文字与难度要求再生成一次'}
                            onClick={() => handleRegenerate(index)}
                          >
                            重新生成
                          </button>
                        </div>
                      )}
                      {features.practice && message.role === 'ASSISTANT' && message.status !== 'FAILED' && (
                        <div className="message-actions">
                          <button
                            type="button"
                            className="btn small"
                            onClick={() => setPracticeFor(practiceFor === message.id ? null : message.id)}
                          >
                            {practiceFor === message.id ? '收起练习' : '举一反三'}
                          </button>
                        </div>
                      )}
                      {features.wrongBook && message.role === 'ASSISTANT' && message.status !== 'FAILED' && index > 0 && (
                        <div className="message-actions">
                          <button
                            type="button"
                            className="btn small"
                            onClick={() => {
                              const source = detail?.messages[index - 1]
                              if (source) void guard(async () => {
                                await markWrong(source.id, '数据结构')
                              })
                            }}
                          >
                            加入错题本
                          </button>
                        </div>
                      )}
                      {practiceFor === message.id && <PracticeCard messageId={message.id} />}
                    </div>
                  )) : <p className="muted">暂无消息。新建会话后在下方输入题目。</p>}
                  {streamingText && (
                    <div className="message assistant">
                      <span className="role">助手 · 生成中</span>
                      <SolutionView content={streamingText} streaming />
                    </div>
                  )}
                </div>

                <Composer
                  disabled={false}
                  busy={busy}
                  guideMode={detail?.session.guideMode ?? 0}
                  onSubmit={handleSubmit}
                />
              </section>
            </>
          )}
        </main>
      </div>
    </div>
  )
}
