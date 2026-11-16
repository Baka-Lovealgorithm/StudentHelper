import { useCallback, useEffect, useState } from 'react'
import {
  createMcpServer, deleteMcpServer, listMcpServers, testMcpServer, updateMcpServer,
  type McpServerItem, type McpTestResult,
} from '../api/mcpServers'
import RowActionMenu from './RowActionMenu'
import FormDialog from './FormDialog'

/** T-301 远程 MCP（DD-10）：仅 http/https 远程传输，不支持 stdio；连接失败不影响解题。 */
export default function McpPanel() {
  const [items, setItems] = useState<McpServerItem[]>([])
  const [name, setName] = useState('')
  const [transport, setTransport] = useState('STREAMABLE_HTTP')
  const [url, setUrl] = useState('')
  const [results, setResults] = useState<Record<number, McpTestResult>>({})
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(false)

  const refresh = useCallback(async () => {
    setItems(await listMcpServers())
  }, [])

  useEffect(() => {
    void refresh().catch((e) => setError(String(e)))
  }, [refresh])

  const run = (action: () => Promise<unknown>, message: string) => void (async () => {
    setError(null)
    setNotice(null)
    try {
      await action()
      await refresh()
      setNotice(message)
    } catch (e) {
      setError(String(e))
    }
  })()

  const runTest = (id: number) => void (async () => {
    setError(null)
    setNotice(null)
    try {
      const result = await testMcpServer(id)
      setResults((prev) => ({ ...prev, [id]: result }))
      await refresh()
      setNotice(result.status === 'OK' ? `连接成功，发现 ${result.toolCount} 个工具` : '连接失败，详情见下方')
    } catch (e) {
      setError(String(e))
    }
  })()

  return (
    <section className="card">
      <h2>MCP 工具</h2>
      <p className="muted">
        只支持远程传输（STREAMABLE_HTTP / SSE），不支持 stdio，也不会启动本机子进程。
        只有同时满足「开关开启 + Server 启用」才会在解题时连接；连接失败只影响工具，不影响答题。
      </p>
      {error && !formOpen && <p className="error banner">操作失败：{error}</p>}
      {notice && !formOpen && <p className="muted">{notice}</p>}

      <ul className="knowledge-list">
        {items.map((item) => {
          const result = results[item.id]
          return (
            <li key={item.id}>
              <div>
                <strong>{item.name}</strong>
                {item.enabled ? <span className="tag">已启用</span> : <span className="tag">已停用</span>}
                <p className="muted">{item.transport} · {item.url}</p>
                <p className="muted">
                  状态：{item.lastStatus ?? '未测试'}
                  {item.lastStatus === 'OK' ? `（${item.toolCount} 个工具）` : ''}
                  {item.lastError ? ` · ${item.lastError.slice(0, 120)}` : ''}
                </p>
                {result && result.tools.length > 0 && (
                  <p className="muted">工具：{result.tools.join('、')}</p>
                )}
              </div>
              <div className="message-actions">
                <button type="button" className="btn" onClick={() => runTest(item.id)}>测试连接</button>
                <RowActionMenu
                  label={`MCP Server 操作：${item.name}`}
                  items={[
                    { key: 'toggle', label: item.enabled ? '停用' : '启用',
                      onSelect: () => run(() => updateMcpServer(item.id, {
                        name: item.name, transport: item.transport, url: item.url, enabled: !item.enabled,
                      }), item.enabled ? '已停用' : '已启用') },
                    { key: 'delete', label: '删除 Server', danger: true,
                      onSelect: () => run(() => deleteMcpServer(item.id), '已删除') },
                  ]}
                />
              </div>
            </li>
          )
        })}
        {items.length === 0 && <li className="muted">暂无 MCP Server</li>}
      </ul>

      <div className="message-actions">
        <button type="button" className="btn primary"
          onClick={() => { setError(null); setNotice(null); setFormOpen(true) }}>
          + 新增 Server
        </button>
      </div>

      <FormDialog
        open={formOpen}
        title="新增 MCP Server"
        onClose={() => setFormOpen(false)}
        footer={(
          <>
            <button type="button" className="btn" onClick={() => setFormOpen(false)}>取消</button>
            <button type="button" className="btn primary" onClick={() => run(async () => {
              await createMcpServer({ name, transport, url, enabled: true })
              setName('')
              setUrl('')
              setFormOpen(false)
            }, 'MCP Server 已新增并启用')}>
              新增 Server
            </button>
          </>
        )}
      >
        {error && <p className="error banner">操作失败：{error}</p>}
        <label className="field">
          名称
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="例如：local-tools" />
        </label>
        <label className="field">
          传输方式
          <select value={transport} onChange={(e) => setTransport(e.target.value)}>
            <option value="STREAMABLE_HTTP">STREAMABLE_HTTP</option>
            <option value="SSE">SSE</option>
          </select>
        </label>
        <label className="field">
          地址
          <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://example.com/mcp" />
        </label>
      </FormDialog>
    </section>
  )
}
