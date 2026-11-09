import type { ReactNode } from 'react'

export type ViewKey =
  | 'chat' | 'config' | 'usage' | 'features' | 'knowledge' | 'profile'
  | 'wrongBook' | 'subject' | 'skill' | 'mcp' | 'stats'

interface NavItem {
  key: ViewKey
  label: string
  icon: string
  /** 受哪个功能开关控制；为空表示常驻可用。 */
  feature?: string
  group: 'main' | 'manage' | 'settings'
}

/** 侧边栏导航：所有功能入口常驻，受开关控制的在关闭态置灰。 */
const NAV_ITEMS: NavItem[] = [
  { key: 'chat', label: '对话', icon: '💬', group: 'main' },
  { key: 'skill', label: '技能', icon: '🧩', feature: 'skill', group: 'manage' },
  { key: 'mcp', label: 'MCP 工具', icon: '🔌', feature: 'mcp', group: 'manage' },
  { key: 'usage', label: '用量看板', icon: '📊', feature: 'usagePanel', group: 'manage' },
  { key: 'wrongBook', label: '错题本', icon: '📕', feature: 'wrongBook', group: 'manage' },
  { key: 'config', label: '模型配置', icon: '⚙️', group: 'manage' },
  { key: 'knowledge', label: '知识点', icon: '📚', feature: 'knowledgeExtract', group: 'manage' },
  { key: 'profile', label: '记忆画像', icon: '🧠', feature: 'memory', group: 'manage' },
  { key: 'stats', label: '学情统计', icon: '📈', feature: 'learningStats', group: 'manage' },
  { key: 'subject', label: '学科', icon: '🎓', feature: 'subjectTemplate', group: 'manage' },
  { key: 'features', label: '功能开关', icon: '🎛️', group: 'settings' },
]

const GROUP_LABELS: Record<NavItem['group'], string> = {
  main: '工作区',
  manage: '功能',
  settings: '设置',
}

interface Props {
  view: ViewKey
  onViewChange: (view: ViewKey) => void
  features: Record<string, boolean>
  collapsed: boolean
  onToggleCollapsed: () => void
  /** 会话列表：仅在展开且处于「对话」视图时显示。 */
  sessionSlot?: ReactNode
}

export default function Sidebar({
  view, onViewChange, features, collapsed, onToggleCollapsed, sessionSlot,
}: Props) {
  let lastGroup: NavItem['group'] | null = null

  return (
    <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>
      <div className="sidebar-top">
        {!collapsed && <span className="sidebar-brand">功能导航</span>}
        <button
          type="button"
          className="collapse-btn"
          onClick={onToggleCollapsed}
          title={collapsed ? '展开侧边栏' : '收起侧边栏'}
          aria-label={collapsed ? '展开侧边栏' : '收起侧边栏'}
        >
          {collapsed ? '»' : '«'}
        </button>
      </div>

      <nav className="side-nav">
        {NAV_ITEMS.map((item) => {
          const enabled = !item.feature || Boolean(features[item.feature])
          const showGroup = !collapsed && item.group !== lastGroup
          lastGroup = item.group
          return (
            <div key={item.key} className="side-nav-entry">
              {showGroup && <div className="side-nav-group">{GROUP_LABELS[item.group]}</div>}
              <button
                type="button"
                className={`side-nav-item ${view === item.key ? 'active' : ''} ${enabled ? '' : 'locked'}`}
                onClick={() => onViewChange(enabled ? item.key : 'features')}
                title={enabled ? item.label : `「${item.label}」当前已关闭，点击前往功能开关开启`}
              >
                <span className="side-nav-icon" aria-hidden="true">{item.icon}</span>
                {!collapsed && <span className="side-nav-label">{item.label}</span>}
                {!collapsed && !enabled && <span className="side-nav-lock" aria-hidden="true">·</span>}
              </button>
            </div>
          )
        })}
      </nav>

      {!collapsed && view === 'chat' && sessionSlot && (
        <div className="sidebar-sessions">{sessionSlot}</div>
      )}
    </aside>
  )
}
