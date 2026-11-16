/**
 * mathText.ts 的回归断言。前端未引入测试框架，用 Node 22 的 type stripping 直接跑 TS：
 *   npm run check:math
 *
 * 覆盖两类历史缺陷：
 *  1. \[...\] 归一化后尾部多出一个孤立 $（模板字面量误写 $$$ 结尾）；
 *  2. \boxed{\text{纯中文}} 被 KaTeX 绘制成穿过文字的一根竖线。
 */
import assert from 'node:assert/strict'
import { prepareMarkdown, normalizeMath, degradeCjkOnlyMath } from '../src/utils/mathText.ts'
import { upgradeKatexSizingClasses } from '../src/utils/katexClassCompat.ts'

// 1) 会话 22 第 9 轮：\boxed{\text{栈为空}} 必须降级成普通文本，不再进入 KaTeX
const bad = '## ⑤ 最终答案\n\n用栈判断括号匹配时，扫描结束后的合法条件是：\n\n\\[\n\\boxed{\\text{栈为空}}\n\\]\n\n完整地说……'
const fixed = prepareMarkdown(bad)
assert.ok(!fixed.includes('$$'), 'display math 定界符应被移除')
assert.ok(!fixed.includes('\\boxed'), 'boxed 命令应被移除')
assert.ok(!fixed.includes('$'), '降级后不得残留孤立 $ 定界符')
assert.ok(fixed.includes('栈为空'), '中文答案文本不能丢')
assert.ok(fixed.includes('## ⑤ 最终答案'), '其余 Markdown 结构不能受影响')

// 2) 混合公式必须保留 KaTeX 渲染（历史回答里另外 3 处 \text{中文}）
const mixed = '\\[ \\text{最坏时间复杂度}=\\Theta(n\\log n) \\]'
assert.equal(
  prepareMarkdown(mixed),
  '$$ \\text{最坏时间复杂度}=\\Theta(n\\log n) $$',
  'display 公式应恰好以两个 $ 收尾，数学命令不能丢',
)

// 3) 普通公式 / 行内公式 / 纯文本不受影响；CR-055：KaTeX 类名映射精确且幂等
assert.equal(normalizeMath('\\(x^2\\)'), '$x^2$')
assert.equal(normalizeMath('\\[x^2\\]'), '$$x^2$$')
assert.equal(normalizeMath('普通文字'), '普通文字')
assert.equal(degradeCjkOnlyMath('$$x^2 + 1$$'), '$$x^2 + 1$$')
const hast = {
  type: 'root',
  children: [{
    type: 'element', tagName: 'span',
    properties: { className: ['sizing', 'reset-size6', 'size3', 'mtight'] },
    children: [{
      type: 'element', tagName: 'span',
      properties: { className: 'sizing reset-size3 size1' }, children: [],
    }],
  }],
}
assert.equal(upgradeKatexSizingClasses(hast), 2, '两个含旧类名的节点应被升级')
assert.deepEqual(hast.children[0].properties.className,
  ['katex-sizing', 'reset-size6', 'size3', 'mtight'], '数组形态只改独立 sizing 项')
assert.equal(hast.children[0].children[0].properties.className,
  'katex-sizing reset-size3 size1', '字符串形态同样生效')
assert.equal(upgradeKatexSizingClasses(hast), 0, '重复执行幂等')
const untouched = { type: 'root', properties: { className: ['size3', 'sizing2', 'fontsize-ensurer'] }, children: [] }
assert.equal(upgradeKatexSizingClasses(untouched), 0, '相似类名与其它类名不误伤')

// 4) 含数字的中文文本盒仍是公式，不误伤
const withNumber = '$$\\boxed{\\text{最大值为 }5}$$'
assert.equal(prepareMarkdown(withNumber), withNumber)

// 5) 幂等
assert.equal(prepareMarkdown(fixed), fixed)

console.log('mathText: 5 组断言全部通过')
