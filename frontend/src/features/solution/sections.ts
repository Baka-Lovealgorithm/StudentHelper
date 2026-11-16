/**
 * 解答分段与门控规则（CR-043 / 候选 08）。
 *
 * <p>改造前这些规则住在 `SolutionView.tsx` 里且全部 module-private：三个纯函数无法导出，
 * 「1=思路 2=关键步骤 3=答案」只存在于一行下标算术里，首帧 `unlocked` 还是 3（先全放行再收起）。
 * 现在规则独立成 module，可脱离 React 直接被 Node 断言。</p>
 */

export interface Section {
  title: string
  body: string
}

const MARKERS = ['①', '②', '③', '④', '⑤', '⑥', '⑦']

/** CR-039：知识点讲解默认五段，与后端 SectionSchema.DEFAULT_EXPLAIN 保持一致。 */
export const DEFAULT_EXPLAIN_TITLES = [
  '## ① 概念定义', '## ② 关键性质与适用条件', '## ③ 典型例子',
  '## ④ 与相邻概念的区别', '## ⑤ 常见误区',
]

export const DISCLAIMER = '本内容由 AI 生成，请自行核对'

/** 解析 sections_json；失败 / 哨兵值返回 null（调用方按默认六段渲染）。 */
export function parseSections(
  json: string | null | undefined,
): { full: string[]; idea: string[]; explain: string[] } | null {
  if (!json || json === 'FREE_TEXT' || json === 'EXPLAIN') return null
  try {
    const parsed = JSON.parse(json) as { full?: unknown; idea?: unknown; explain?: unknown }
    const normalize = (value: unknown): string[] =>
      Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string' && v.startsWith('## ')) : []
    const full = normalize(parsed.full)
    const idea = normalize(parsed.idea)
    const explain = normalize(parsed.explain)
    return full.length > 0 && idea.length > 0 ? { full, idea, explain } : null
  } catch {
    return null
  }
}

/** CR-039：intent 优先；历史消息无 intent 时按落库结构推断（FREE_TEXT → 追问）。 */
export function isFreeText(
  intent: 'SOLVE' | 'FOLLOW_UP' | 'EXPLAIN' | null | undefined,
  sectionSchema: string | null | undefined,
): boolean {
  return intent ? intent === 'FOLLOW_UP' : sectionSchema === 'FREE_TEXT'
}

/** 讲解结构：优先学科自定义 explain 数组，否则默认五段。 */
export function explainTitlesOf(sectionSchema: string | null | undefined): string[] {
  const parsed = sectionSchema ? parseSections(sectionSchema) : null
  return parsed && parsed.explain.length > 0 ? parsed.explain : DEFAULT_EXPLAIN_TITLES
}

/**
 * 后端落库时会在末尾追加提示行（可能因模型模仿而重复多次）。
 * 这里先剥掉，由输入框下方统一渲染一次，否则正文会渲染一遍、底部再渲染一遍。
 */
export function splitDisclaimer(content: string): { body: string; hasDisclaimer: boolean } {
  let body = content.trim()
  let has = false
  while (body.endsWith(DISCLAIMER)) {
    has = true
    body = body.slice(0, body.length - DISCLAIMER.length).trim()
  }
  return { body, hasDisclaimer: has }
}

/**
 * CR-018：按 sections_json 声明的标题切段；未带 schema（旧消息 / 默认六段）时
 * 沿用 S1 的圈号 + 「补充：/ 附加：」识别，与后端 `SolutionSectionParser` 同口径。
 */
export function splitSections(
  content: string,
  sectionSchema: string | null | undefined,
  titlesOverride?: string[],
): { prefix: string; sections: Section[] } {
  const schema = sectionSchema ? parseSections(sectionSchema) : null
  const knownTitles = new Set<string>()
  const titles = titlesOverride ?? (schema ? [...schema.full, ...schema.idea] : [])
  for (const title of titles) {
    knownTitles.add(title.replace(/^##\s+/, ''))
  }
  const lines = content.split('\n')
  let prefix = ''
  const sections: Section[] = []
  let current: Section | null = null
  for (const line of lines) {
    const trimmed = line.trim()
    const sectionTitle = trimmed.startsWith('## ') ? trimmed.slice(3) : ''
    const extra = sectionTitle.startsWith('补充：') || sectionTitle.startsWith('附加：')
    const declared = knownTitles.size > 0 && knownTitles.has(sectionTitle)
    const legacy = MARKERS.some((m) => sectionTitle.startsWith(m))
    if (trimmed.startsWith('## ') && (extra || declared || (knownTitles.size === 0 && legacy))) {
      if (current) sections.push(current)
      current = { title: sectionTitle, body: '' }
    } else if (current) {
      current.body += (current.body ? '\n' : '') + line
    } else {
      prefix += (prefix ? '\n' : '') + line
    }
  }
  if (current) sections.push(current)
  return { prefix: prefix.trim(), sections }
}

/**
 * D-11：复制文本 = 引导文字 + 各段「标题 + 正文」，段间空行分隔。
 * 标题按界面显示口径去掉 `## ` 前缀；不含末尾免责声明提示行。
 */
export function composeAnswer(prefix: string, sections: Section[]): string {
  const blocks = sections
    .map((section) => {
      const body = section.body.trim()
      return body ? `${section.title}\n${body}` : section.title
    })
    .filter((block) => block.trim().length > 0)
  return [prefix.trim(), ...blocks].filter((block) => block.length > 0).join('\n\n')
}

/** 分步解锁是否生效：讲解 / 追问 / 流式中 / 段数不足都不门控。 */
export function isGated(
  options: {
    stepUnlockEnabled: boolean
    streaming: boolean
    isExplain: boolean
    sectionCount: number
  },
): boolean {
  return options.stepUnlockEnabled && !options.streaming && !options.isExplain
    && options.sectionCount > 3
}

/**
 * 门控下可见段落：前 3 段恒可见；第 4 段需解锁到 2 级；第 5 段起需 3 级。
 * 复制按钮用同一函数取可见集，避免出现「看得到但复制不到」或反之。
 */
export function visibleSections(sections: Section[], unlocked: number, gated: boolean): Section[] {
  if (!gated) return sections
  return sections.filter((_, index) => index < 3 || (index === 3 ? unlocked >= 2 : unlocked >= 3))
}
