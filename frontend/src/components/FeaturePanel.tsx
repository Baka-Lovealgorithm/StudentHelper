import { useCallback, useEffect, useState } from 'react'
import { listFeatures, setFeature, type FeatureToggle } from '../api/features'

interface Props {
  /** 把开关快照同步给 App，用于按开关显示 / 隐藏入口。 */
  onChange?: (snapshot: Record<string, boolean>) => void
}

/** S2 逐功能独立开关（DD-01）：默认全关，开启后对应能力才写入 S2 表。 */
export default function FeaturePanel({ onChange }: Props) {
  const [features, setFeatures] = useState<FeatureToggle[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busyKey, setBusyKey] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    const rows = await listFeatures()
    setFeatures(rows)
    onChange?.(Object.fromEntries(rows.map((r) => [r.key, r.enabled])))
  }, [onChange])

  useEffect(() => {
    void refresh().catch((e) => setError(String(e)))
  }, [refresh])

  const toggle = (item: FeatureToggle) => void (async () => {
    setBusyKey(item.key)
    setError(null)
    try {
      await setFeature(item.key, !item.enabled)
      await refresh()
    } catch (e) {
      setError(String(e))
    } finally {
      setBusyKey(null)
    }
  })()

  return (
    <section className="card">
      <h2>S2 功能开关</h2>
      <p className="muted">
        每项独立控制，默认全部关闭。关闭时该能力不写入任何 S2 数据表，S1 解题主链路与耗时统计保持不变。
      </p>
      {error && <p className="error banner">操作失败：{error}</p>}
      <div className="feature-list">
        {features.map((item) => (
          <label key={item.key} className="feature-row">
            <input
              type="checkbox"
              checked={item.enabled}
              disabled={busyKey === item.key}
              onChange={() => toggle(item)}
            />
            <span className="feature-name">{item.label}</span>
            <span className="feature-task">{item.task}</span>
            <span className={`feature-state ${item.enabled ? 'on' : 'off'}`}>
              {item.enabled ? '已开启' : '已关闭'}
            </span>
          </label>
        ))}
      </div>
    </section>
  )
}
