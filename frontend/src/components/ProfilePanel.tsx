import { useCallback, useEffect, useState } from 'react'
import {
  clearMemory, consolidateMemory, getMemory, getMemoryFile, updateMemoryUser,
  type MemoryOverview,
} from '../api/memory'
import ConfirmDialog from './ConfirmDialog'

/**
 * T-401：长期记忆面板。
 * MEMORY.md 分「用户手写区 / 模型维护区」，这里只编辑用户区；模型区由 Consolidation 维护。
 */
export default function ProfilePanel() {
  const [overview, setOverview] = useState<MemoryOverview | null>(null)
  const [userDraft, setUserDraft] = useState('')
  const [selected, setSelected] = useState('MEMORY.md')
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  /** CR-069：清空记忆前用居中确认框二次确认（原来是原生 confirm）。 */
  const [confirmClear, setConfirmClear] = useState(false)

  const refresh = useCallback(async () => {
    const data = await getMemory()
    setOverview(data)
    setUserDraft(data.user)
    setContent(await getMemoryFile(selected))
  }, [selected])

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

  const dirty = overview != null && userDraft !== overview.user

  /** CR-069：确认后先关弹窗再执行，沿用既有 `run` 的成功提示与错误处理。 */
  const confirmClearMemory = () => {
    setConfirmClear(false)
    run(clearMemory, '记忆已清空并归档')
  }

  return (
    <section className="card">
      <h2>长期记忆</h2>
      <p className="muted">
        记忆以 Markdown 文件保存在本地：按天追加的流水账 + 一个长期记忆索引。
        模型会在对话压缩时把值得记住的内容写入当日流水账，每天最多整理 3 次到长期记忆；
        你可以直接编辑「用户手写区」，模型不会覆盖它。
      </p>
      {error && <p className="error banner">操作失败：{error}</p>}
      {notice && <p className="muted">{notice}</p>}

      <div className="profile-grid">
        <div>
          <h3>用户手写区</h3>
          <label className="field">
            内容（支持 Markdown）
            <textarea
              value={userDraft}
              onChange={(e) => setUserDraft(e.target.value)}
              rows={8}
              placeholder="例如：## 学习偏好&#10;- 讲通俗些，少用公式&#10;- 复杂度要给出推导"
            />
          </label>
          <div className="message-actions">
            <button
              type="button"
              className="btn"
              disabled={!dirty}
              onClick={() => run(
                () => updateMemoryUser(userDraft, overview?.version ?? ''),
                '用户手写区已保存',
              )}
            >
              保存
            </button>
            <button
              type="button"
              className="btn"
              onClick={() => void (async () => {
                setError(null)
                setNotice(null)
                try {
                  const result = await consolidateMemory()
                  await refresh()
                  setNotice(`整理结果：${result.status}${result.detail ? ' · ' + result.detail : ''}`)
                } catch (e) {
                  setError(String(e))
                }
              })()}
            >
              整理记忆
            </button>
            <button
              type="button"
              className="btn"
              onClick={() => setConfirmClear(true)}
            >
              清空
            </button>
          </div>
        </div>

        <div>
          <h3>记忆文件</h3>
          <ul className="plain-list">
            {(overview?.files ?? []).map((file) => (
              <li key={file.name}>
                <button
                  type="button"
                  className={file.name === selected ? 'btn small' : 'btn small ghost'}
                  onClick={() => setSelected(file.name)}
                >
                  {file.name}
                </button>
                <span className="muted"> {file.size} B</span>
              </li>
            ))}
            {overview != null && overview.files.length === 0 && (
              <li className="muted">暂无记忆文件，写入或整理后出现。</li>
            )}
          </ul>
          <p className="muted">
            注入预算：{overview?.injectMaxChars ?? 2000} 字符；超出后模型可用记忆检索工具取全文。
          </p>
        </div>
      </div>

      <h3>{selected}</h3>
      <pre className="memory-view">{content || '（空）'}</pre>

      {/* CR-069：清空记忆改用页面内居中确认框 */}
      <ConfirmDialog
        open={confirmClear}
        title="清空长期记忆"
        message="清空会先归档当前记忆文件，再恢复为空模板。确定继续？"
        confirmText="清空"
        onConfirm={confirmClearMemory}
        onCancel={() => setConfirmClear(false)}
      />
    </section>
  )
}
