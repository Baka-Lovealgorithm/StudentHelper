interface Props {
  stage: string
  busy: boolean
  error: string | null
  timing?: { ttft: number; total: number } | null
}

const LABELS: Record<string, string> = {
  UNDERSTANDING: '正在理解题目…',
  GENERATING: '正在生成解答…',
  DONE: '生成完成',
  FAILED: '生成失败',
  STOPPED: '已停止生成',
}

export default function AgentStatusBar({ stage, busy, error, timing }: Props) {
  if (error) return <div className="status-bar error">{error}</div>
  if (!busy && !stage) return null
  return (
    <div className={busy ? 'status-bar active' : 'status-bar'}>
      {LABELS[stage] ?? (busy ? '处理中…' : '就绪')}
      {timing && ` · 首字 ${(timing.ttft / 1000).toFixed(1)}s · 总耗时 ${(timing.total / 1000).toFixed(1)}s`}
    </div>
  )
}
