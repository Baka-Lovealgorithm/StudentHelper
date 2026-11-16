import type { ReactNode } from 'react'

import { NAV_KEYS, VIEWS, type ViewDef, type ViewKey } from '../views'

export type { ViewKey }
export { NAV_KEYS }

/** CR-070：分组标题；顺序由 `VIEWS` 数组顺序决定（工作区 → 基础设置 → 学科 → 个性化 → 数据统计）。 */
const GROUP_LABELS: Record<ViewDef['group'], string> = {
  main: '工作区',
  basics: '基础设置',
  subject: '学科',
  personal: '个性化',
  data: '数据统计',
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
  let lastGroup: ViewDef['group'] | null = null

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
        {VIEWS.map((item) => {
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
