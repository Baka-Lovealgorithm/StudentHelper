import { useCallback, useEffect, useState } from 'react'
import { listFeatures, setFeature, type FeatureToggle } from '../api/features'

interface Props {
  /** 把开关快照同步给 App，用于按开关显示 / 隐藏入口。 */
  onChange?: (snapshot: Record<string, boolean>) => void
}

/** CR-021：三类分栏。归类只在前端，后端接口与开关键名不变。 */
type CategoryKey = 'qa' | 'subject' | 'extension'

interface Category {
  key: CategoryKey
  label: string
  hint: string
}

const CATEGORIES: Category[] = [
  { key: 'qa', label: '问答相关', hint: '改变一次问答的输出形态或上下文' },
  { key: 'subject', label: '学科相关', hint: '学科规范、知识点、错题与练习' },
  { key: 'extension', label: 'MCP、skill 等扩展', hint: '工具、技能、文件工作区与用量观测' },
]

/**
 * 归类映射：键名与后端 `S2Feature.key` 一一对应。
 * 用户 2026-11-11 裁决：用量看板归扩展；CR-041 起反馈闭环已退役，不再有该项。
 */
const FEATURE_CATEGORY: Record<string, CategoryKey> = {
  stepUnlock: 'qa',
  multiSolution: 'qa',
  memory: 'qa',
  conversationCompaction: 'qa',
  historyImage: 'qa',
  knowledgeExtract: 'subject',
  learningStats: 'subject',
  wrongBook: 'subject',
  practice: 'subject',
  mcp: 'extension',
  skill: 'extension',
  usagePanel: 'extension',
}

/** 未登记的新开关兜底归第三类，避免它从界面上消失。 */
const categoryOf = (key: string): CategoryKey => FEATURE_CATEGORY[key] ?? 'extension'
const isClassified = (key: string): boolean => key in FEATURE_CATEGORY

/** CR-030：记住上次选中的分类页签；非法 / 缺失值回退「问答相关」。 */
const CATEGORY_KEY = 'studenthelper.featureCategory'
const restoreCategory = (): CategoryKey => {
  const raw = localStorage.getItem(CATEGORY_KEY)
  return raw && CATEGORIES.some((item) => item.key === raw) ? (raw as CategoryKey) : 'qa'
}

/** S2 逐功能独立开关（DD-01）：默认全关，开启后对应能力才写入 S2 表。 */
export default function FeaturePanel({ onChange }: Props) {
  const [features, setFeatures] = useState<FeatureToggle[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [category, setCategory] = useState<CategoryKey>(restoreCategory)

  const refresh = useCallback(async () => {
    const rows = await listFeatures()
    setFeatures(rows)
    onChange?.(Object.fromEntries(rows.map((r) => [r.key, r.enabled])))
  }, [onChange])

  useEffect(() => {
    void refresh().catch((e) => setError(String(e)))
  }, [refresh])

  /** CR-030：页签选择写入 localStorage；切换仍不请求接口、不改开关状态。 */
  useEffect(() => {
    localStorage.setItem(CATEGORY_KEY, category)
  }, [category])

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

  const visible = features.filter((item) => categoryOf(item.key) === category)

  return (
    <section className="card">
      <h2>功能开关</h2>
      <p className="muted">
        按类别分组，只显示所选类别的功能。每项独立控制、默认全部关闭；关闭时该能力不写入任何 S2 数据表，
        S1 解题主链路与耗时统计保持不变。
      </p>
      {error && <p className="error banner">操作失败：{error}</p>}

      <div className="feature-tabs" role="tablist" aria-label="功能类别">
        {CATEGORIES.map((tab) => (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={category === tab.key}
            title={tab.hint}
            className={category === tab.key ? 'feature-tab active' : 'feature-tab'}
            onClick={() => setCategory(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="feature-list" role="tabpanel">
        {visible.length === 0 && <p className="muted">这一类暂时没有可配置的功能。</p>}
        {visible.map((item) => (
          <div key={item.key}>
          <label className="feature-row">
            <input
              type="checkbox"
              checked={item.enabled}
              disabled={busyKey === item.key}
              onChange={() => toggle(item)}
            />
            <span className="feature-name">{item.label}</span>
            {!isClassified(item.key) && <span className="feature-tag">未分类</span>}
            <span className="feature-task">{item.task}</span>
            <span className={`feature-state ${item.enabled ? 'on' : 'off'}`}>
              {item.enabled ? '已开启' : '已关闭'}
            </span>
          </label>
          </div>
        ))}
      </div>
    </section>
  )
}
