import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchHealth, type HealthData } from './api/client'
import { cancelGeneration, submitQuestion } from './api/chat'
import {
  createSession, deleteSession, getSessionDetail, listSessionsPage, renameSession,
  setGuideMode, setSessionModel,
  type SessionDetail, type SessionItem,
} from './api/session'
import SubjectSkillSelector from './components/SubjectSkillSelector'
import SessionSkillSelector from './components/SessionSkillSelector'
import SubjectSkillPanel from './components/SubjectSkillPanel'
import SessionList from './components/SessionList'
import Sidebar from './components/Sidebar'
import ModelConfigPanel from './components/ModelConfigPanel'
import ModelSelector from './components/ModelSelector'
import UsagePanel from './components/UsagePanel'
import Composer, { type ComposerPart } from './components/Composer'
import SolutionView from './components/SolutionView'
import AgentStatusBar from './components/AgentStatusBar'
import FeaturePanel from './components/FeaturePanel'
import { listFeatures } from './api/features'
import KnowledgePanel from './components/KnowledgePanel'
import ProfilePanel from './components/ProfilePanel'
import SkillPanel from './components/SkillPanel'
import McpPanel from './components/McpPanel'
import StatsPanel from './components/StatsPanel'
import ErrorBoundary from './components/ErrorBoundary'
import ConfirmDialog from './components/ConfirmDialog'
import ProcessPanel from './components/ProcessPanel'
import WrongBookPanel from './components/WrongBookPanel'
import { markWrong } from './api/wrongBook'
import { groupProcess, imageUrl, listProcess, type ProcessItem } from './api/process'
import { stripSessionTitle } from './utils/sessionTitle'
import {
  canRender, fallbackView, isRootPath, NAV_KEYS, pathFor, viewFromPath,
  type ViewKey,
} from './views'
import {
  applyStreamEvent, createDeltaBatcher, initialStreamState, parseStreamEvent,
  type StreamEvent, type StreamState,
} from './chat/streamEvents'

const STORAGE_KEY = 'studenthelper.currentSessionId'
const SIDEBAR_KEY = 'studenthelper.sidebarCollapsed'
const VIEW_KEY = 'studenthelper.currentView'
const PAGE_SIZE = 10

/** CR-066：举一反三固定提示词——点击后以用户身份自动发送，出题走正常对话流（只出题、不给答案）。 */
const PRACTICE_PROMPT = '请根据上面这道题，出一道考查相同知识点、难度相近的相似题。只输出题目本身，不要给出答案、提示或解题过程。'

/**
 * CR-061：错题本标记学科。解答消息的 subjectLabel 形如「数据结构 · 6 段完整结构」，
 * 取「 · 」前的学科名；追问轮 / 本轮未采用学科规范时为 null，回落后端默认的「数据结构」。
 */
function subjectOfLabel(label: string | null | undefined): string {
  const name = label?.split(' · ')[0]?.trim()
  return name ? name : '数据结构'
}

/**
 * CR-032：助手身份标识。历史消息与流式「生成中」块共用同一套圆形头像 + 名称，
 * 头像为内联 SVG 且 aria-hidden，不引入图片资源、网络请求或第三方依赖。
 */
function AssistantIdentity({ status }: { status: string }) {
  return (
    <div className="message-head assistant-head">
      <span className="assistant-avatar" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 3 2 8l10 5 10-5-10-5Z" fill="currentColor" />
          <path
            d="M6 10.7V15c0 1.6 2.7 2.8 6 2.8s6-1.2 6-2.8v-4.3"
            stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
          />
          <path d="M21 8v6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      </span>
      <span className="assistant-name">解题助手</span>
      <span className="role">· {status}</span>
    </div>
  )
}

/**
 * CR-050：地址优先恢复视图。
 * - 命中路径表 → 按路径恢复（优先于 localStorage，刷新 / 手输地址都生效）；
 * - 根地址 → 用 localStorage 记忆兜底，并把地址补写成对应路径；
 * - 未知 / 过期路径（如历史残留的 /models）→ 对话页。
 */
const restoreView = (): ViewKey => {
  const pathname = window.location.pathname
  const fromPath = viewFromPath(pathname)
  if (fromPath) return fromPath
  if (isRootPath(pathname)) {
    const raw = localStorage.getItem(VIEW_KEY)
    return raw && (NAV_KEYS as string[]).includes(raw) ? (raw as ViewKey) : 'chat'
  }
  return 'chat'
}


export default function App() {
  const [view, setView] = useState<ViewKey>(() => {
    const initial = restoreView()
    // 首次载入：把根 / 未知路径规范成真实路径（replaceState，不新增历史记录）。
    if (window.location.pathname !== pathFor(initial)) {
      window.history.replaceState({ view: initial }, '', pathFor(initial))
    }
    return initial
  })
  /** CR-050：首次同步与 popstate 恢复用 replaceState，其余视图切换用 pushState。 */
  const firstSyncRef = useRef(true)
  const poppingRef = useRef(false)

  /** CR-030：记住上次所在页面；覆盖所有 setView 路径（含开关关闭时的强制回退）。 */
  useEffect(() => {
    localStorage.setItem(VIEW_KEY, view)
  }, [view])

  /** CR-050：浏览器前进 / 后退按地址恢复视图；未知路径回退对话页。 */
  useEffect(() => {
    const onPopState = () => {
      poppingRef.current = true
      setView(viewFromPath(window.location.pathname) ?? 'chat')
    }
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])
  /** 侧边栏收起状态，刷新后保留。 */
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === '1')
  /** 会话列表分批加载：首屏 PAGE_SIZE 条，只提供「加载更多」，不做页码跳转。 */
  const [sessions, setSessions] = useState<SessionItem[]>([])
  const [sessionPage, setSessionPage] = useState(0)
  const [hasMoreSessions, setHasMoreSessions] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  /** S2 开关快照（DD-01），默认空对象即全部关闭。 */
  const [features, setFeatures] = useState<Record<string, boolean>>({})
  const [currentId, setCurrentId] = useState<number | null>(() => {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? Number(raw) : null
  })
  const [detail, setDetail] = useState<SessionDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [health, setHealth] = useState<HealthData | null>(null)
  const [busy, setBusy] = useState(false)
  /** CR-053：本轮生成所属会话（首轮会话是提交后才创建的，不能用 currentId）。 */
  const activeSessionRef = useRef<number | null>(null)
  /** CR-043：一次请求的流式状态合并为一个对象，由 streamEvents 的 reducer 整体替换。 */
  const [stream, setStream] = useState<StreamState>(initialStreamState)
  /** CR-027：历史过程（按消息分组）与流式期间的过程快照。 */
  const [processByMessage, setProcessByMessage] = useState<Record<number, ProcessItem[]>>({})
  /** CR-028：当前页内放大的题面图片（不再跳新标签页）。 */
  const [lightbox, setLightbox] = useState<string | null>(null)
  /** CR-068：待删除的会话（点「删除会话」只置目标、由居中弹窗二次确认）。 */
  const [deleteTarget, setDeleteTarget] = useState<{ id: number; title: string } | null>(null)

  /** 统一错误处理：业务异常显示到主区顶部横幅，不中断页面。 */
  const guard = async (action: () => Promise<void>) => {
    try {
      setError(null)
      await action()
    } catch (e) {
      setError(String(e))
    }
  }

  /** 重新拉取首屏（新建 / 删除后调用），回到第 0 页。 */
  const refreshSessions = useCallback(async () => {
    const first = await listSessionsPage(0, PAGE_SIZE)
    setSessions(first.items)
    setSessionPage(0)
    setHasMoreSessions(first.hasMore)
  }, [])

  /** 「加载更多」：追加下一批，按 id 去重，避免新建会话后翻页造成重复。 */
  const loadMoreSessions = () => void guard(async () => {
    if (loadingMore || !hasMoreSessions) return
    setLoadingMore(true)
    try {
      const next = sessionPage + 1
      const page = await listSessionsPage(next, PAGE_SIZE)
      setSessions((previous) => {
        const seen = new Set(previous.map((item) => item.id))
        return [...previous, ...page.items.filter((item) => !seen.has(item.id))]
      })
      setSessionPage(next)
      setHasMoreSessions(page.hasMore)
    } finally {
      setLoadingMore(false)
    }
  })

  /** 重命名：局部更新列表与当前会话标题，不整页刷新，保持已加载的批次。 */
  const handleRename = (id: number, title: string) => void guard(async () => {
    const renamed = await renameSession(id, title)
    setSessions((previous) => previous.map((item) => (item.id === id ? renamed : item)))
    if (currentId === id) {
      setDetail((previous) => previous ? { ...previous, session: renamed } : previous)
    }
  })

  /** CR-012：切换当前会话的模型，局部更新详情，不重刷消息列表。 */
  const handleModelChange = (modelId: number) => void guard(async () => {
    if (!currentId) return
    const updated = await setSessionModel(currentId, modelId)
    setDetail((previous) => previous ? { ...previous, session: updated } : previous)
  })

  /** CR-018：会话级三态切换同样只局部更新详情。 */
  const handleSubjectChange = (session: SessionItem) => {
    setDetail((previous) => previous ? { ...previous, session } : previous)
  }

  /** CR-038：会话固化技能切换只局部更新详情，不重开会话。 */
  const handleSessionSkillsChange = (session: SessionItem) => {
    setDetail((previous) => previous ? { ...previous, session } : previous)
  }

  const toggleCollapsed = () => {
    setCollapsed((previous) => {
      localStorage.setItem(SIDEBAR_KEY, previous ? '0' : '1')
      return !previous
    })
  }

  /** 稳定引用，避免 FeaturePanel 的 useEffect 因回调变化反复请求。 */
  /** CR-043：关闭态回退改为查注册表——新增受控页面不再需要改这里。 */
  const syncFeatures = useCallback((snapshot: Record<string, boolean>) => {
    setFeatures(snapshot)
    setView((current) => fallbackView(current, snapshot) ?? current)
  }, [])

  const openSession = useCallback(async (id: number) => {
    const result = await getSessionDetail(id)
    setDetail(result)
    setCurrentId(id)
    localStorage.setItem(STORAGE_KEY, String(id))
    setView('chat')
    setLightbox(null)
    // CR-027：模型行为过程按消息分组回读；失败不影响会话打开
    try {
      setProcessByMessage(groupProcess(await listProcess(id)))
    } catch {
      setProcessByMessage({})
    }
  }, [])

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
    void fetchHealth().then(setHealth).catch(() => setHealth(null))
    // 首次加载 S2 开关快照；失败按全部关闭处理
    void listFeatures()
      .then((rows) => syncFeatures(Object.fromEntries(rows.map((r) => [r.key, r.enabled]))))
      .catch(() => setFeatures({}))
    // 首次挂载恢复上次会话
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /** CR-028：Esc 退出页内图片放大。CR-063：放大层只做纯预览，不再有复制状态。 */
  useEffect(() => {
    if (!lightbox) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setLightbox(null)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [lightbox])

  /* CR-036：滚动条改由 CSS :hover 控制显隐，不再需要 JS 的 is-scrolling 滚动委托。 */

  /**
   * CR-048：新建会话不再立即落库——只切到空白对话页。
   * 首次发消息时由 handleSubmit 的 `if (!sessionId) createSession()` 分支创建会话，
   * 用户没有输入就不会在库里留下空会话，也无需事后再「移除」。
   */
  const handleCreate = () => {
    localStorage.removeItem(STORAGE_KEY)
    setCurrentId(null)
    setDetail(null)
    setProcessByMessage({})
    setStream(initialStreamState)
    setLightbox(null)
    setError(null)
    setView('chat')
  }

  /**
   * CR-068：点击「删除会话」只记下目标并弹居中确认框（原来是 `window.confirm` 的浏览器顶部原生框）。
   * 标题取自已加载列表，缺失时回落「该会话」。
   */
  const handleDelete = (id: number) => {
    const title = sessions.find((item) => item.id === id)?.title?.trim() || '该会话'
    setDeleteTarget({ id, title })
  }

  /** CR-068：弹窗确认后才真正删——先关弹窗再请求，避免请求期间遮罩残留、也防止重复提交。 */
  const confirmDelete = () => void guard(async () => {
    const target = deleteTarget
    if (!target) return
    setDeleteTarget(null)
    await deleteSession(target.id)
    if (currentId === target.id) {
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

  const handleSubmit = (text: string, hint: string, images: File[], parts: ComposerPart[] = [], practice = false) => {
    void (async () => {
      setBusy(true)
      setStream({ ...initialStreamState, stage: 'UNDERSTANDING' })
      setError(null)
      const batcher = createDeltaBatcher((chunk) => {
        setStream((previous) => ({ ...previous, text: previous.text + chunk }))
      })
      // CR-049 / D-15：后端 error 事件也要让本轮判为失败，否则正文会被当成成功而清空
      let terminalError: string | null = null
      let stopped = false
      const dispatch = (event: StreamEvent) => {
        if (event.kind === 'delta') {
          batcher.push(event.data.text ?? '')
          return
        }
        if (event.kind === 'error') {
          if (event.data.code === 'E1006') {
            // CR-053：用户主动停止——不写红色错误条，状态条显示「已停止生成」
            stopped = true
            setStream((previous) => ({ ...previous, stage: 'STOPPED' }))
            return
          }
          terminalError = `${event.data.code ?? 'E5000'}：${event.data.message ?? '生成失败'}`
        }
        setStream((previous) => applyStreamEvent(previous, event))
      }
      let succeeded = false
      let sessionId = currentId
      try {
        if (!sessionId) {
          const created = await createSession()
          sessionId = created.id
          await openSession(sessionId)
          await refreshSessions()
        }
        activeSessionRef.current = sessionId
        // D-20：提交后立即乐观插入用户消息——否则要等本轮结束 openSession 回读才出现，
        // 举一反三自动发送的提示词会与助手回答「一同出现」。完成后由真实消息整体替换。
        setDetail((previous) => previous ? {
          ...previous,
          messages: [...previous.messages, {
            id: -Date.now(),
            role: 'USER',
            content: text,
            hint: hint || null,
            imagePaths: null,
            status: 'SUCCESS',
            errorCode: null,
            sectionSchema: null,
            subjectLabel: null,
            intent: null,
            createdAt: new Date().toISOString(),
          }],
        } : previous)
        await submitQuestion(
          { sessionId, text, hint, images, parts, guideMode: detail?.session.guideMode ?? 0, practice },
          (event) => {
            const parsed = parseStreamEvent(event)
            if (parsed) dispatch(parsed)
          },
        )
        batcher.flush()
        await openSession(sessionId)
        await refreshSessions()
        succeeded = terminalError === null && !stopped
      } catch (e) {
        batcher.flush()
        // CR-049 / D-15：断流 / 超时不再静默成功——错误写进对话区状态条，已收到的正文保留
        const message = terminalError ?? String(e).replace(/^Error:\s*/, '')
        setStream((previous) => ({
          ...previous,
          stage: 'FAILED',
          error: previous.error ?? message,
        }))
        // 失败轮次也回读详情（后端会把该轮 USER 消息标为 FAILED）；重载失败不掩盖原错误
        if (sessionId) {
          try {
            await openSession(sessionId)
            await refreshSessions()
          } catch { /* 详情重载失败不影响错误展示 */ }
        }
      } finally {
        setBusy(false)
        // CR-043 / CR-049：只有完整成功才清流式缓冲；失败时保留部分答案供用户查看
        // CR-053：停止轮次已把半截正文落库为中断消息，同样清空流式缓冲避免重复显示
        if (succeeded || stopped) {
          setStream((previous) => ({ ...previous, text: '', process: [] }))
        }
      }
    })()
  }

  /** CR-053：停止本轮生成；由后端 dispose 模型订阅并落库中断消息。 */
  const handleStop = () => {
    const sessionId = activeSessionRef.current
    if (!busy || !sessionId) return
    void guard(async () => {
      await cancelGeneration(sessionId)
    })
  }

  /** CR-043：渲染守卫只此一处——开关关闭时按对话页渲染，不再是每个分支各判一次。 */
  const currentView: ViewKey = canRender(view, features) ? view : 'chat'

  /** CR-050：以实际渲染视图为准同步地址栏，覆盖导航点击、打开会话、新建会话与开关回退。
   *  放在 currentView 之后，避免引用尚未声明的开关快照（TS2448 / TS2454）。 */
  useEffect(() => {
    const popping = poppingRef.current
    poppingRef.current = false
    const first = firstSyncRef.current
    // 首次渲染时开关快照还没加载，用记忆里的视图即可；之后一律以实际渲染视图为准。
    const rendered: ViewKey = first ? view : currentView
    const target = pathFor(rendered)
    if (window.location.pathname === target) {
      firstSyncRef.current = false
      return
    }
    if (first || popping) {
      window.history.replaceState({ view: rendered }, '', target)
      firstSyncRef.current = false
    } else {
      window.history.pushState({ view: rendered }, '', target)
    }
  }, [view, currentView])

  /** CR-048：空白对话页（未落库的新会话，或 0 条消息的会话）把输入框移到卡片垂直中央。
   *  排除 busy / 流式内容，避免「已提交但消息还没落库」的瞬间被误判成空态导致布局跳动。 */
  const isEmptyChat = !busy && !stream.text && stream.process.length === 0
    && (!detail || detail.messages.length === 0)

  /** CR-066：仅最后一条助手消息可触发「举一反三」（语义是「基于刚讲完的这题再练一道」）。 */
  const lastAssistantIndex = detail
    ? detail.messages.reduce((last, message, index) => (message.role === 'ASSISTANT' ? index : last), -1)
    : -1

  return (
    <div className={`app-shell ${collapsed ? 'sidebar-collapsed' : ''}`}>
      {/* CR-044：去掉顶部品牌栏，侧栏与内容区整体上移；仅保留右上角连接状态浮标。 */}
      <span
        className={`conn conn-float ${health ? 'up' : 'down'}`}
        title={health ? `${health.app} ${health.status}` : '后端未连接'}
      >
        {health ? '后端已连接' : '后端未连接'}
      </span>

      <div className="layout">
        <Sidebar
          view={view}
          onViewChange={setView}
          features={features}
          collapsed={collapsed}
          onToggleCollapsed={toggleCollapsed}
          sessionSlot={
            <SessionList
              sessions={sessions}
              currentId={currentId}
              hasMore={hasMoreSessions}
              loadingMore={loadingMore}
              onCreate={handleCreate}
              onSelect={(id) => void guard(() => openSession(id))}
              onDelete={handleDelete}
              onRename={handleRename}
              onLoadMore={loadMoreSessions}
            />
          }
        />

        <main className={currentView === 'chat' ? 'workspace workspace-chat' : 'workspace'}>
          <ErrorBoundary key={currentView}>
          {error && <p className="error banner">操作失败：{error}</p>}

          {currentView === 'features' ? <FeaturePanel onChange={syncFeatures} />
            : currentView === 'profile' ? <ProfilePanel />
            : currentView === 'wrongBook'
              ? <WrongBookPanel onOpenSession={(id) => void guard(() => openSession(id))} />
            : currentView === 'knowledge' ? <KnowledgePanel />
            : currentView === 'usage' ? <UsagePanel />
            : currentView === 'stats' ? <StatsPanel />
            : currentView === 'subjectSkill' ? <SubjectSkillPanel />
            : currentView === 'skill' ? <SkillPanel />
            : currentView === 'mcp' ? <McpPanel />
            : currentView === 'config' ? <ModelConfigPanel /> : (
            <>
              {/* CR-045：对话题目上移到内容区顶部左上角（与右上角连接浮标同一行），卡片内不再渲染标题行。 */}
              <div className="chat-title">
                <h2 title={detail?.session.title ?? undefined}>{detail?.session.title ?? '新会话'}</h2>
              </div>

              <section className={`card chat-card ${isEmptyChat ? 'chat-card-empty' : ''}`}>
                <AgentStatusBar stage={stream.stage} busy={busy} error={stream.error} timing={stream.timing} />

                <div className="messages">
                  {detail && detail.messages.length > 0 ? detail.messages.map((message, index) => (
                    <div key={message.id} className={`message ${message.role.toLowerCase()}`}>
                      {/* CR-032：助手用头像 + 名称显著标识；用户消息正常态不显示状态行，异常态仍保留状态 / 错误码。 */}
                      {message.role === 'ASSISTANT' ? (
                        <AssistantIdentity
                          status={message.errorCode === 'E1006'
                            ? '已停止'
                            : `${message.status}${message.errorCode ? ` · ${message.errorCode}` : ''}`}
                        />
                      ) : (
                        (message.status !== 'SUCCESS' || message.errorCode) && (
                          <div className="message-head user-head">
                            <span className="role">我 · {message.status}{message.errorCode ? ` · ${message.errorCode}` : ''}</span>
                          </div>
                        )
                      )}
                      {message.role === 'USER' && message.hint && (
                        <p className="message-hint">难度 / 讲解要求：{message.hint}</p>
                      )}
                      {message.role === 'USER' && message.imagePaths && (
                        <div className="message-images">
                          {message.imagePaths.split(',').filter((part) => part.trim()).map((path, idx) => (
                            <button
                              key={idx}
                              type="button"
                              className="image-thumb"
                              onClick={() => setLightbox(imageUrl(path))}
                              aria-label={`放大查看题面图片 ${idx + 1}`}
                            >
                              <img src={imageUrl(path)} alt={`题面图片 ${idx + 1}`} loading="lazy" />
                            </button>
                          ))}
                        </div>
                      )}
                      {message.role === 'ASSISTANT' && message.subjectLabel && message.status !== 'FAILED' && (
                        <p className="subject-used-tag">本次采用：{message.subjectLabel}</p>
                      )}
                      {/* CR-054 / D-18：停止轮次（FAILED / E1006）已落库的过程同样回显；
                          普通失败消息仍不展示过程面板，成功消息口径不变。 */}
                      {message.role === 'ASSISTANT'
                        && (message.status !== 'FAILED' || message.errorCode === 'E1006')
                        && (processByMessage[message.id]?.length ?? 0) > 0 && (
                        <ProcessPanel items={processByMessage[message.id]}
                          interrupted={message.errorCode === 'E1006'} />
                      )}
                      {message.role === 'ASSISTANT'
                        ? (message.status === 'FAILED'
                            ? (message.errorCode === 'E1006'
                                ? <>
                                    <p className="stopped-tag">已停止生成，以下为已生成内容</p>
                                    <SolutionView content={message.content ?? ''}
                                      stepUnlockEnabled={features.stepUnlock}
                                      messageId={message.id}
                                      sectionSchema={message.sectionSchema}
                                      intent={message.intent}
                                      onZoomImage={(url) => setLightbox(url)} />
                                  </>
                                : <details className="raw-answer">
                                    <summary>生成结果格式异常，点击查看模型原始输出</summary>
                                    <pre>{message.content ?? ''}</pre>
                                  </details>)
                            : <SolutionView content={message.content ?? ''}
                                stepUnlockEnabled={features.stepUnlock}
                                messageId={message.id}
                                sectionSchema={message.sectionSchema}
                                intent={message.intent}
                                /* CR-062：答案图点击放大，复用题面图的页内放大层 */
                                onZoomImage={(url) => setLightbox(url)}
                                /* TCR-060：消息级操作与复制按钮同一行（传入 SolutionView 的答案操作行） */
                                actions={((features.practice && index === lastAssistantIndex)
                                  || (features.wrongBook && index > 0)) ? (
                                  <>
                                    {features.practice && index === lastAssistantIndex && (
                                      <button
                                        type="button"
                                        className="icon-action"
                                        title="举一反三"
                                        aria-label="举一反三"
                                        disabled={busy}
                                        onClick={() => handleSubmit(PRACTICE_PROMPT, '', [], [], true)}
                                      >
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                                          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                          <path d="M9 18h6" /><path d="M10 21h4" />
                                          <path d="M12 3a6 6 0 0 0-4 10.5c.6.5 1 1.6 1 2.5h6c0-.9.4-2 1-2.5A6 6 0 0 0 12 3Z" />
                                        </svg>
                                      </button>
                                    )}
                                    {features.wrongBook && index > 0 && (
                                      <button
                                        type="button"
                                        className="icon-action"
                                        title="加入错题本"
                                        aria-label="加入错题本"
                                        onClick={() => {
                                          const source = detail?.messages[index - 1]
                                          if (source) void guard(async () => {
                                            await markWrong(source.id, subjectOfLabel(message.subjectLabel))
                                          })
                                        }}
                                      >
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                                          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                          <path d="M6 3h12v18l-6-4-6 4V3Z" /><path d="M12 7v6" /><path d="M9 10h6" />
                                        </svg>
                                      </button>
                                    )}
                                  </>
                                ) : null} />)
                        : <p>{message.content ?? '（无文本内容）'}</p>}
                    </div>
                  )) : <p className="muted">暂无消息。在输入框中输入题目开始解答。</p>}
                  {/* CR-031：过程面板与正文各自按数据条件渲染，首个 process 事件即可见，
                      不必等 streamingText 有值；正文为空时不渲染 SolutionView 空壳。 */}
                  {(stream.process.length > 0 || stream.text) && (
                    <div className="message assistant">
                      <AssistantIdentity status={stream.stage === 'FAILED' ? '生成中断' : '生成中'} />
                      {/* CR-049 / D-15：断流 / 超时时保留的正文必须标注为不完整 */}
                      {stream.stage === 'FAILED' && (
                        <p className="stream-incomplete">生成未完成，以下为已收到的部分内容，可重新提问。</p>
                      )}
                      {stream.process.length > 0 && <ProcessPanel items={stream.process} streaming />}
                      {/* CR-033：剥离标题行后再判断正文是否开始，避免空壳（AC-502-8）。 */}
                      {stripSessionTitle(stream.text, true) && (
                        <SolutionView content={stream.text} streaming
                          onZoomImage={(url) => setLightbox(url)} />
                      )}
                    </div>
                  )}
                </div>

                <Composer
                  disabled={false}
                  busy={busy}
                  guideMode={detail?.session.guideMode ?? 0}
                  onGuideModeChange={handleGuideMode}
                  onSubmit={handleSubmit}
                  onStop={handleStop}
                  subjectSlot={
                    <SubjectSkillSelector
                      sessionId={currentId}
                      mode={detail?.session.subjectMode ?? 'AUTO'}
                      subjectSkillId={detail?.session.subjectSkillId ?? null}
                      enabled
                      disabled={busy}
                      onChanged={handleSubjectChange}
                      onError={(message) => setError(message)}
                    />
                  }
                  skillSlot={
                    <SessionSkillSelector
                      sessionId={currentId}
                      skillIds={detail?.session.skillIds ?? []}
                      enabled={Boolean(features.skill)}
                      disabled={busy}
                      embedded
                      onChanged={handleSessionSkillsChange}
                      onError={(message) => setError(message)}
                    />
                  }
                  modelSlot={
                    <ModelSelector
                      sessionId={currentId}
                      modelId={detail?.session.modelId ?? null}
                      onChanged={handleModelChange}
                      disabled={busy}
                    />
                  }
                />
              </section>
            </>
          )}
          </ErrorBoundary>
        </main>
      </div>

      {/* CR-028：屏幕中央放大（不占满全屏），× 位于图片下方 */}
      {lightbox && (
        <div
          className="image-lightbox-backdrop"
          role="presentation"
          onClick={() => setLightbox(null)}
        >
          <div
            className="image-lightbox"
            role="dialog"
            aria-modal="true"
            aria-label="图片预览"
            onClick={(e) => e.stopPropagation()}
          >
            <img src={lightbox} alt="图片预览" />
            <button
              type="button"
              className="image-lightbox-close"
              onClick={() => setLightbox(null)}
              aria-label="关闭图片预览"
            >
              ×
            </button>
          </div>
        </div>
      )}
      {/* CR-068：删除会话改为页面内居中弹窗，取代浏览器顶部原生确认框 */}
      <ConfirmDialog
        open={deleteTarget !== null}
        title="删除会话"
        message={(
          <>
            确定删除会话「{deleteTarget?.title ?? '该会话'}」及其全部消息？该操作不可恢复。
          </>
        )}
        confirmText="删除"
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  )
}
