/**
 * CR-055 / D-19：KaTeX 类名版本兼容（TCR-082）。
 *
 * rehype-katex@7.0.1 依赖 katex@0.16.x，其 renderToString 输出旧类名 `sizing`；
 * 而 MarkdownRenderer 加载的是顶层 katex@0.19.1 的 CSS，121 条
 * `.katex .katex-sizing.reset-sizeN.sizeM { font-size: … }` 规则不认旧类名。
 * 失配时行内 \frac 的分子分母不会按 scriptstyle 缩小，却仍按缩小后的 em 度量
 * 定位，分母含指数时分子与指数笔画重合（CDP 实测 7.5px / 12.3px）。
 *
 * 在 hast 层把独立的 `sizing` 类名映射成 `katex-sizing`，即可让 0.19 的规则
 * 命中；不动 npm 依赖、不复制第三方 CSS。插件必须排在 rehypeKatex 之后。
 */

/** hast 节点中只用到 className 与 children，避免为此引入类型依赖。 */
interface HastLike {
  properties?: { className?: unknown; [key: string]: unknown }
  children?: HastLike[]
}

const CLASS_ALIAS: Record<string, string> = { sizing: 'katex-sizing' }

function mapClasses(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map((entry) =>
      typeof entry === 'string' ? CLASS_ALIAS[entry] ?? entry : entry)
  }
  if (typeof value === 'string') {
    return value.split(/\s+/).map((entry) => CLASS_ALIAS[entry] ?? entry).join(' ')
  }
  return value
}

/** 原地遍历 hast 树升级类名；返回被改写的节点数，供回归断言使用。 */
export function upgradeKatexSizingClasses(tree: HastLike): number {
  let changed = 0
  const walk = (node: HastLike) => {
    const props = node.properties
    if (props && 'className' in props) {
      const before = props.className
      const after = mapClasses(before)
      const differs = Array.isArray(before) && Array.isArray(after)
        ? before.length !== after.length || before.some((entry, i) => entry !== after[i])
        : before !== after
      if (differs) {
        props.className = after
        changed++
      }
    }
    node.children?.forEach(walk)
  }
  walk(tree)
  return changed
}

/** rehype 插件：参数为外部 hast 树，unknown 兼容 unified 的 Transformer 签名。 */
export function rehypeKatexSizingCompat() {
  return (tree: unknown) => { upgradeKatexSizingClasses(tree as HastLike) }
}
