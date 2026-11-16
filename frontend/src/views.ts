/**
 * 视图注册表（CR-043 / 候选 07）。
 *
 * <p>改造前「功能 key ↔ 视图」的对应关系声明了三次：`Sidebar.NAV_ITEMS[].feature`、
 * `App.syncFeatures` 里 7 条手写 `if`、渲染处 11 层嵌套三元。加一个受开关控制的页面要改三处，
 * 漏改第三处就能通过 `setView` 绕过开关（历史回归 D-14 正是这一类）。
 * 现在只有这一张表：导航渲染、关闭态回退、渲染守卫都查它。</p>
 */

export type ViewKey =
  | 'chat' | 'config' | 'usage' | 'features' | 'knowledge' | 'profile'
  | 'wrongBook' | 'subjectSkill' | 'skill' | 'mcp' | 'stats'

export interface ViewDef {
  key: ViewKey
  label: string
  icon: string
  /** CR-050：该视图在地址栏中的唯一路径。 */
  path: string
  /** 受哪个功能开关控制；省略表示常驻可用。 */
  feature?: string
  /** CR-070：侧栏分组；数组顺序即展示顺序，相邻同组才输出组标题。 */
  group: 'main' | 'basics' | 'subject' | 'personal' | 'data'
}

/**
 * 侧边栏导航：所有入口常驻，受开关控制的在关闭态置灰且不可进入。
 *
 * <p>CR-070：原先「功能」栏把 9 个入口平铺，语义太泛；现按用途分四类，展示顺序按用户裁决
 * 为「基础设置 → 学科 → 个性化 → 数据统计」（工作区仍在最上）。分组只靠数组顺序 + 相邻同组，
 * 不引入额外排序字段——渲染、关闭态回退、渲染守卫仍共用这一张表（CR-043）。</p>
 */
export const VIEWS: ViewDef[] = [
  { key: 'chat', label: '对话', icon: '💬', path: '/chat', group: 'main' },
  // 基础设置：能力扩展与运行配置的统一入口（CR-070，含原「设置」组的功能开关）
  { key: 'mcp', label: 'MCP 工具', icon: '🔌', path: '/mcp', feature: 'mcp', group: 'basics' },
  { key: 'skill', label: '技能', icon: '🧩', path: '/skills', feature: 'skill', group: 'basics' },
  { key: 'config', label: '模型配置', icon: '⚙️', path: '/model-config', group: 'basics' },
  { key: 'features', label: '功能开关', icon: '🎛️', path: '/features', group: 'basics' },
  // 学科
  { key: 'knowledge', label: '知识点', icon: '📚', path: '/knowledge', feature: 'knowledgeExtract', group: 'subject' },
  // CR-039：学科规范开关已移除，入口常驻可点
  { key: 'subjectSkill', label: '学科规范', icon: '📐', path: '/subject-skills', group: 'subject' },
  { key: 'wrongBook', label: '错题本', icon: '📕', path: '/wrong-book', feature: 'wrongBook', group: 'subject' },
  // 个性化
  { key: 'profile', label: '长期记忆', icon: '🧠', path: '/memory', feature: 'memory', group: 'personal' },
  // 数据统计
  { key: 'stats', label: '学情统计', icon: '📈', path: '/stats', feature: 'learningStats', group: 'data' },
  { key: 'usage', label: '用量看板', icon: '📊', path: '/usage', feature: 'usagePanel', group: 'data' },
]

/** 恢复上次页面时用于校验 localStorage 值。 */
export const NAV_KEYS: ViewKey[] = VIEWS.map((item) => item.key)

/** CR-050：路径去尾斜杠；空路径按根处理。 */
export function normalizePath(pathname: string): string {
  const trimmed = (pathname || '/').replace(/\/+$/, '')
  return trimmed === '' ? '/' : trimmed
}

/** CR-050：视图 → 唯一路径；未知 key 一律回退对话页。 */
export function pathFor(key: ViewKey): string {
  return viewDef(key)?.path ?? '/chat'
}

/** CR-050：严格匹配路径表；根 / 未知路径返回 null，由调用方决定兜底或回退。 */
export function viewFromPath(pathname: string): ViewKey | null {
  const path = normalizePath(pathname)
  return VIEWS.find((item) => item.path === path)?.key ?? null
}

/** CR-050：根地址（含 /index.html）用 localStorage 记忆兜底。 */
export function isRootPath(pathname: string): boolean {
  const path = normalizePath(pathname)
  return path === '/' || path === '/index.html'
}

export function viewDef(key: ViewKey): ViewDef | undefined {
  return VIEWS.find((item) => item.key === key)
}

/** 该视图是否可渲染：无开关约束，或对应开关已开启。 */
export function canRender(key: ViewKey, features: Record<string, boolean>): boolean {
  const def = viewDef(key)
  return !def?.feature || Boolean(features[def.feature])
}

/** 开关关闭时该视图应回退到哪；无需回退返回 null。 */
export function fallbackView(
  key: ViewKey,
  features: Record<string, boolean>,
): ViewKey | null {
  return canRender(key, features) ? null : 'chat'
}
