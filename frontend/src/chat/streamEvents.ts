import type { SseEvent } from '../api/sse'
import type { ProcessItem } from '../api/process'

/**
 * SSE 事件语义（CR-043 / 候选 03）。
 *
 * <p>改造前：协议 DTO 写在 `App.tsx` 里，五类事件的分派直接读写 5 个 state，
 * delta 的 40ms 合批用闭包变量 + 定时器，三者纠缠在 `handleSubmit` 的 60 行里，
 * 无法单测。现在解析与归并都是纯函数，节流单独抽成可注入的 batcher。</p>
 */

/** 后端 SSE 事件的数据体；字段按事件类型可选。 */
export interface StreamEventData {
  stage?: string
  text?: string
  index?: number
  code?: string
  message?: string
  messageId?: number
  ttftMs?: number
  durationMs?: number
  /** CR-027：process 事件字段。 */
  seq?: number
  kind?: string
  title?: string
  detail?: string
  elapsedMs?: number
}

export type StreamEventKind = 'status' | 'delta' | 'process' | 'done' | 'error'

export interface StreamEvent {
  kind: StreamEventKind
  data: StreamEventData
}

/** 一次请求内的流式状态；六个平行 state 合并为一个可整体替换的对象。 */
export interface StreamState {
  stage: string
  text: string
  process: ProcessItem[]
  error: string | null
  timing: { ttft: number; total: number } | null
}

export const initialStreamState: StreamState = {
  stage: '',
  text: '',
  process: [],
  error: null,
  timing: null,
}

const KNOWN_KINDS: Record<string, StreamEventKind> = {
  status: 'status',
  delta: 'delta',
  process: 'process',
  done: 'done',
  error: 'error',
}

/**
 * 把一条原始 SSE 事件转成带类型的流事件。
 * 未知事件类型返回 null（例如后端未来新增的事件，旧前端应忽略而不是报错）；
 * data 非法 JSON 时按空对象处理，与改造前 `try/catch` 忽略的行为一致。
 */
export function parseStreamEvent(event: SseEvent): StreamEvent | null {
  const kind = KNOWN_KINDS[event.event]
  if (!kind) return null
  let data: StreamEventData = {}
  try {
    data = JSON.parse(event.data) as StreamEventData
  } catch {
    data = {}
  }
  return { kind, data }
}

/**
 * 事件归并（纯函数）：给定旧状态与一条事件，返回新状态。
 *
 * <p>process 的规则是「同 seq 覆盖、新 seq 追加」——同一次思考会连续下发多个增量，
 * 用 seq 做键才不会把一次思考拆成几十条。</p>
 */
export function applyStreamEvent(state: StreamState, event: StreamEvent): StreamState {
  switch (event.kind) {
    case 'status':
      return { ...state, stage: event.data.stage ?? 'GENERATING' }
    case 'delta':
      return { ...state, text: state.text + (event.data.text ?? '') }
    case 'process': {
      const seq = event.data.seq
      if (seq === undefined) return state
      const item: ProcessItem = {
        messageId: null,
        seq,
        kind: event.data.kind ?? 'TOOL',
        title: event.data.title ?? '',
        detail: event.data.detail ?? '',
        elapsedMs: event.data.elapsedMs ?? 0,
      }
      const index = state.process.findIndex((row) => row.seq === seq)
      const process = index >= 0
        ? state.process.map((row, i) => (i === index ? item : row))
        : [...state.process, item]
      return { ...state, process }
    }
    case 'done':
      return {
        ...state,
        stage: 'DONE',
        timing: { ttft: event.data.ttftMs ?? 0, total: event.data.durationMs ?? 0 },
      }
    case 'error':
      return {
        ...state,
        stage: 'FAILED',
        error: `${event.data.code ?? 'E5000'}：${event.data.message ?? '生成失败'}`,
      }
    default:
      return state
  }
}

export interface DeltaBatcher {
  /** 追加一段增量；首个增量到达后 intervalMs 内合并成一次 emit。 */
  push(text: string): void
  /** 立即把缓冲区刷出（流结束或异常时必须调用）。 */
  flush(): void
}

/**
 * delta 合批：真实模型一次解题可下发数百个 delta，逐条 setState 会拖慢渲染。
 * 定时器与 emit 都可替换，便于在不依赖真实时钟的情况下测试合批行为。
 */
export function createDeltaBatcher(
  emit: (text: string) => void,
  intervalMs = 40,
): DeltaBatcher {
  let buffer = ''
  let timer: ReturnType<typeof setTimeout> | null = null

  const flush = () => {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
    if (!buffer) return
    const pending = buffer
    buffer = ''
    emit(pending)
  }

  const push = (text: string) => {
    if (!text) return
    buffer += text
    if (timer === null) {
      timer = setTimeout(() => {
        timer = null
        flush()
      }, intervalMs)
    }
  }

  return { push, flush }
}
