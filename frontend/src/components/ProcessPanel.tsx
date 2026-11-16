import { useState } from 'react'
import type { ProcessItem } from '../api/process'

const KIND_LABEL: Record<string, string> = {
  SUBJECT: '学科',
  SKILL: '技能',
  TOOL: '工具',
  THINKING: '思考',
}

/** 思考全文展示上限（技术规范 5.21）。 */
const THINKING_LIMIT = 500

/** CR-028：TOOL 类别中按名称判定的读入型工具；写入型与 MCP 仍展示摘要。 */
const READ_TOOL_NAMES = new Set(['memory_get', 'memory_search'])
/** 工具参数里可作为「读入对象」的字段，按优先级取第一个命中的值。 */
const TARGET_KEYS = ['name', 'code', 'query', 'path', 'q']

/**
 * CR-028：读入类过程只展示「操作 + 对象」，不铺开工具返回的正文。
 * SUBJECT / SKILL 天然是读入；TOOL 里仅记忆检索视为读入，
 * 写入型与 MCP 工具仍按 AC-502-4 展示参数 / 结果摘要。
 */
function isReadOperation(item: ProcessItem): boolean {
  if (item.kind === 'SUBJECT' || item.kind === 'SKILL') return true
  if (item.kind !== 'TOOL') return false
  const toolName = item.title.split('：').pop()?.trim() ?? ''
  return READ_TOOL_NAMES.has(toolName)
}

/** 从 detail 首行「参数：{...}」里提取具体读入目标；解析不出来返回空串。 */
function readTarget(detail: string): string {
  const line = (detail ?? '').split('\n').find((row) => row.startsWith('参数：'))
  if (!line) return ''
  const inner = line.slice('参数：'.length).trim().replace(/^\{/, '').replace(/\}$/, '')
  if (!inner) return ''
  const pairs = inner.split(/,\s*/).map((pair) => pair.trim()).filter(Boolean)
  for (const key of TARGET_KEYS) {
    const hit = pairs.find((pair) => pair.startsWith(`${key}=`))
    if (hit) {
      const value = hit.slice(key.length + 1).trim()
      if (value) return value
    }
  }
  return ''
}

/** 读入类标题：把工具名换成具体读入目标，例如「读入通用技能 · 复杂度标注」。 */
function readLabel(item: ProcessItem): string {
  const target = readTarget(item.detail)
  return target ? `${item.title.split('：')[0]} · ${target}` : item.title
}

interface Props {
  items: ProcessItem[]
  streaming?: boolean
  /** CR-054 / D-18：停止轮次的过程面板，头部用「已停止」而非「已完成」。 */
  interrupted?: boolean
}

/**
 * CR-027：模型行为过程面板。位于正文上方、默认收起，与正文视觉隔离；
 * 思考条目默认只显示一行摘要，点击后展开全文（截断 500 字）。
 * CR-028：读入类条目只显示「操作 · 具体读入目标」，不渲染读入内容。
 * CR-031：流式态默认展开，让模型行为在正文开始输出之前即可见；
 * 完成 / 历史态仍沿用 AC-502-3 的默认收起口径。
 * CR-054 / D-18：停止轮次（E1006）只是头部标签改「已停止」，仍按历史态默认收起。
 */
export default function ProcessPanel({ items, streaming = false, interrupted = false }: Props) {
  const [open, setOpen] = useState(streaming)
  const [expanded, setExpanded] = useState<Set<number>>(new Set())

  if (items.length === 0) return null
  const totalMs = items.reduce((max, item) => Math.max(max, item.elapsedMs), 0)
  const seconds = (totalMs / 1000).toFixed(1)

  const toggle = (seq: number) => {
    setExpanded((previous) => {
      const next = new Set(previous)
      if (next.has(seq)) next.delete(seq)
      else next.add(seq)
      return next
    })
  }

  return (
    <div className="process-panel">
      <button
        type="button"
        className="process-head"
        aria-expanded={open}
        onClick={() => setOpen((previous) => !previous)}
      >
        <span className="process-chevron">{open ? '▾' : '▸'}</span>
        <span>
          {streaming ? '处理中' : interrupted ? '已停止' : '已完成'} · {items.length} 步
          {totalMs > 0 && ` · ${seconds}s`}
        </span>
      </button>
      {open && (
        <ul className="process-list">
          {items.map((item) => {
            const isThinking = item.kind === 'THINKING'
            const isRead = !isThinking && isReadOperation(item)
            const detail = item.detail ?? ''
            const summary = detail.length > 60 ? `${detail.slice(0, 60)}…` : detail
            const shown = expanded.has(item.seq)
            return (
              <li key={item.seq} className="process-row">
                <span className={`process-kind kind-${item.kind.toLowerCase()}`}>
                  {KIND_LABEL[item.kind] ?? '过程'}
                </span>
                <div className="process-body">
                  <p className="process-title" title={isRead ? item.title : undefined}>
                    {isRead ? readLabel(item) : item.title}
                  </p>
                  {isThinking ? (
                    <>
                      {!shown && summary && <p className="process-summary">{summary}</p>}
                      {shown && (
                        <pre className="process-detail">
                          {detail.slice(0, THINKING_LIMIT)}
                          {detail.length > THINKING_LIMIT ? '\n…（已截断）' : ''}
                        </pre>
                      )}
                      {detail.length > 60 && (
                        <button type="button" className="process-toggle" onClick={() => toggle(item.seq)}>
                          {shown ? '收起思考' : '查看思考'}
                        </button>
                      )}
                    </>
                  ) : isRead ? null : (
                    detail && <pre className="process-detail">{detail}</pre>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
