/**
 * views.ts 的回归断言（CR-043 / 候选 07）。
 *   npm run check:views
 *
 * 守护点：开关关闭时任何受控视图都必须回退到对话页——改造前这条规则写在
 * `App.syncFeatures` 的 7 条 if 与 11 层三元里，漏改一处就能绕过开关（D-14）。
 */
import assert from 'node:assert/strict'
import { canRender, fallbackView, NAV_KEYS, VIEWS, viewDef } from '../src/views.ts'

// 1) 每个受控视图都有明确的开关 key，且 key 都在 features 命名空间内
const gated = VIEWS.filter((v) => v.feature)
assert.ok(gated.length >= 7, '受控视图数量不应少于 7')
for (const view of gated) {
  assert.ok(view.feature, `${view.key} 应声明 feature`)
  assert.equal(canRender(view.key, { [view.feature]: true }), true, `${view.key} 开启时应可渲染`)
  assert.equal(canRender(view.key, { [view.feature]: false }), false, `${view.key} 关闭时不可渲染`)
  assert.equal(fallbackView(view.key, { [view.feature]: false }), 'chat', `${view.key} 关闭时应回退对话页`)
  assert.equal(fallbackView(view.key, { [view.feature]: true }), null, `${view.key} 开启时无需回退`)
}

// 2) 常驻视图不受任何开关影响（学科规范在 CR-039 后恒可用）
for (const key of ['chat', 'config', 'features', 'subjectSkill']) {
  assert.equal(canRender(key, {}), true, `${key} 为常驻视图`)
  assert.equal(fallbackView(key, {}), null)
  assert.equal(viewDef(key)?.feature, undefined, `${key} 不应绑定开关`)
}

// 3) 开关未加载（空快照）时受控视图一律不可渲染，避免闪现空面板
for (const view of gated) {
  assert.equal(canRender(view.key, {}), false, `${view.key} 在开关未加载时不可渲染`)
}

// 4) 导航键完整且无重复
assert.equal(new Set(NAV_KEYS).size, NAV_KEYS.length, '导航键不得重复')
assert.equal(NAV_KEYS.length, VIEWS.length)
assert.ok(NAV_KEYS.includes('chat'), '对话页必须是合法视图')

console.log(`views: ${gated.length * 4 + 12} 项断言全部通过`)
