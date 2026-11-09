/**
 * 模型输出 → Markdown 渲染前的纯文本归一化。
 * 独立于 React，便于用 Node 直接跑断言（本仓库前端未引入测试框架）。
 */

const CJK = /[\u3400-\u9fff\u3000-\u303f\uff00-\uffef]/
/** 出现这些字符说明是真公式，绝不能降级成纯文本。 */
const MATH_TOKEN = /[A-Za-z0-9\\^_{}=$+\-*/<>]/

/** 模型常输出 \(...\) / \[...\]，统一转成 remark-math 能识别的 $...$ / $$...$$。
 *  注意模板字面量里 `$$${expr}$$` 的前两个 $ 是字面量、第三个 $ 才开始插值；
 *  结尾曾误写成三个 $，导致每个 display 公式尾部多出一个孤立 $（2026-11-09 修复）。 */
export function normalizeMath(input: string): string {
  return input
    .replace(/\\\(([\s\S]*?)\\\)/g, (_m, expr: string) => `$${expr}$`)
    .replace(/\\\[([\s\S]*?)\\\]/g, (_m, expr: string) => `$$${expr}$$`)
}

/**
 * KaTeX 0.19 的字体度量表不含 CJK：\text{中文} 在 \boxed 内会让 stretchy 边框按异常宽度绘制，
 * 视觉上退化成穿过文字的一根竖线（2026-11-09 会话 22 第 9 轮 \boxed{\text{栈为空}} 实测，
 * katex.min.css 中亦无 .cjk_fallback 规则）。
 *
 * 只含中文 / 标点、不含任何数学符号的 display math 文本盒降级为普通 Markdown 段落；
 * 形如 \text{最坏时间复杂度}=\Theta(n\log n) 的混合公式保持 KaTeX 渲染，回答内容不丢失。
 */
export function degradeCjkOnlyMath(input: string): string {
  return input.replace(/\$\$([\s\S]*?)\$\$/g, (whole, expr: string) => {
    const stripped = expr
      .replace(/\\boxed\s*\{/g, '')
      .replace(/\\text\s*\{([^{}]*)\}/g, '$1')
      .replace(/[{}]/g, '')
      .trim()
    if (!stripped || !CJK.test(stripped) || MATH_TOKEN.test(stripped)) {
      return whole
    }
    return `\n\n${stripped}\n\n`
  })
}

/** 渲染管线：先统一公式定界符，再降级纯中文文本盒。 */
export function prepareMarkdown(input: string): string {
  return degradeCjkOnlyMath(normalizeMath(input))
}
