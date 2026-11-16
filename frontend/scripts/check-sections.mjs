/**
 * sections.ts 的回归断言（CR-043 / 候选 08）。
 *   npm run check:sections
 *
 * 与后端 SolutionSectionParser 同口径：六段切分、补充/附加截断、门控可见集、复制文本。
 */
import assert from 'node:assert/strict'
import {
  composeAnswer, isFreeText, isGated, parseSections, splitDisclaimer, splitSections,
  visibleSections,
} from '../src/features/solution/sections.ts'

// 1) 六段切分：无 schema 时按圈号识别，前言进 prefix
const answer = [
  '先说明一下这道题的背景。',
  '## ① 题目理解',
  '求栈的弹出序列。',
  '## ② 涉及知识点',
  '栈 > 后进先出',
  '## ③ 解题思路',
  '按入栈顺序模拟。',
  '## ④ 详细步骤',
  '第一步……',
  '## ⑤ 最终答案',
  '3 2 1',
  '## ⑥ 易错点 / 验算',
  '注意空栈。',
].join('\n')

const parsed = splitSections(answer, null)
assert.equal(parsed.prefix, '先说明一下这道题的背景。', '首段之前的文字进 prefix')
assert.equal(parsed.sections.length, 6, '应切成六段')
assert.equal(parsed.sections[0].title, '① 题目理解', '标题去掉 ## 前缀')
assert.equal(parsed.sections[4].body.trim(), '3 2 1')

// 2) 「补充：/ 附加：」也算段（历史回答里的可选段落，CR-024 仍要能渲染）
const withExtra = splitSections('## ① a\n内容\n## 补充：参考代码\nint x;', null)
assert.equal(withExtra.sections.length, 2, '补充段必须被识别')
assert.equal(withExtra.sections[1].title, '补充：参考代码')

// 3) 声明式 schema：只认声明的标题，未声明的 ## 行归入正文
const schemaJson = JSON.stringify({
  full: ['## ① 已知', '## ② 求', '## ③ 过程', '## ④ 答案'],
  idea: ['## ① 已知', '## ② 求'],
})
assert.equal(parseSections(schemaJson)?.full.length, 4)
assert.equal(parseSections('FREE_TEXT'), null, '哨兵值不解析')
assert.equal(parseSections('EXPLAIN'), null, '哨兵值不解析')
assert.equal(parseSections('not-json'), null, '脏数据不抛错')

const declared = splitSections('## ① 已知\nx\n## ⑤ 结论\n不在声明里', schemaJson)
assert.equal(declared.sections.length, 1, '未声明的标题不切段')
assert.ok(declared.sections[0].body.includes('## ⑤ 结论'), '未声明标题作为正文保留')

// 4) 提示行剥离（模型可能重复多次）
assert.equal(splitDisclaimer('答案\n本内容由 AI 生成，请自行核对').body, '答案')
assert.equal(
  splitDisclaimer('答案\n本内容由 AI 生成，请自行核对\n本内容由 AI 生成，请自行核对').body,
  '答案',
  '重复的提示行必须全部剥掉',
)

// 5) 自由文本判定：intent 优先，历史消息按落库结构推断
assert.equal(isFreeText('FOLLOW_UP', null), true)
assert.equal(isFreeText('SOLVE', 'FREE_TEXT'), false, 'intent 优先于落库结构')
assert.equal(isFreeText(null, 'FREE_TEXT'), true, '历史消息无 intent 时按结构推断')
assert.equal(isFreeText(null, null), false)

// 6) 门控：讲解 / 流式 / 段数不足都不门控
assert.equal(isGated({ stepUnlockEnabled: true, streaming: false, isExplain: false, sectionCount: 6 }), true)
assert.equal(isGated({ stepUnlockEnabled: true, streaming: true, isExplain: false, sectionCount: 6 }), false)
assert.equal(isGated({ stepUnlockEnabled: true, streaming: false, isExplain: true, sectionCount: 5 }), false)
assert.equal(isGated({ stepUnlockEnabled: true, streaming: false, isExplain: false, sectionCount: 3 }), false)

// 7) 可见集：前 3 段恒可见；第 4 段需 2 级；第 5 段起需 3 级
const six = ['①', '②', '③', '④', '⑤', '⑥'].map((t) => ({ title: t, body: t }))
assert.equal(visibleSections(six, 1, true).length, 3)
assert.equal(visibleSections(six, 2, true).length, 4)
assert.equal(visibleSections(six, 3, true).length, 6)
assert.equal(visibleSections(six, 1, false).length, 6, '未门控时全可见')

// 8) 复制文本与可见集一致（不能靠复制绕过解锁）
const visible = visibleSections(six, 2, true)
const copied = composeAnswer('前言', visible)
assert.ok(copied.startsWith('前言'), '复制文本含引导文字')
assert.ok(copied.includes('④'), '已解锁段可复制')
assert.ok(!copied.includes('⑤'), '未解锁段不得出现在复制文本里')

console.log('sections: 24 项断言全部通过')
