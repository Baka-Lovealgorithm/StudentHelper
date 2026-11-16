/**
 * streamEvents.ts 的回归断言。前端未引入测试框架，沿用 check-math-text.mjs 的做法：
 *   npm run check:stream
 *
 * 覆盖三类历史缺陷：
 *  1. process 事件同 seq 必须覆盖而不是追加（否则一次思考被拆成几十条）；
 *  2. 未知事件类型与非法 JSON 不得抛错，也不得改变状态；
 *  3. delta 合批必须只 emit 一次（数百个 delta 逐条 setState 会拖慢渲染）。
 */
import assert from 'node:assert/strict'
import {
  applyStreamEvent, createDeltaBatcher, initialStreamState, parseStreamEvent,
} from '../src/chat/streamEvents.ts'

const sse = (event, data) => ({ event, data: JSON.stringify(data) })

// 1) 五类事件归并
let state = applyStreamEvent(initialStreamState, parseStreamEvent(sse('status', { stage: 'GENERATING' })))
assert.equal(state.stage, 'GENERATING')

state = applyStreamEvent(state, parseStreamEvent(sse('delta', { text: '## ① ' })))
state = applyStreamEvent(state, parseStreamEvent(sse('delta', { text: '题目理解' })))
assert.equal(state.text, '## ① 题目理解', 'delta 必须按到达顺序拼接')

state = applyStreamEvent(state, parseStreamEvent(sse('process', { seq: 1, kind: 'THINKING', title: '思考' })))
state = applyStreamEvent(state, parseStreamEvent(sse('process', { seq: 1, kind: 'THINKING', detail: '增量' })))
assert.equal(state.process.length, 1, '同 seq 必须覆盖，不能追加成两条')
assert.equal(state.process[0].detail, '增量', '覆盖后保留最新内容')

state = applyStreamEvent(state, parseStreamEvent(sse('process', { seq: 2, kind: 'TOOL', title: 'subject_get' })))
assert.equal(state.process.length, 2, '新 seq 追加')
assert.equal(state.process[1].title, 'subject_get')

const done = applyStreamEvent(state, parseStreamEvent(sse('done', { ttftMs: 1200, durationMs: 9000 })))
assert.equal(done.stage, 'DONE')
assert.deepEqual(done.timing, { ttft: 1200, total: 9000 })

const failed = applyStreamEvent(done, parseStreamEvent(sse('error', { code: 'E2002', message: '模型调用失败' })))
assert.equal(failed.stage, 'FAILED')
assert.equal(failed.error, 'E2002：模型调用失败')
assert.equal(failed.text, done.text, '失败不得清空已收到的正文（历史缺陷：finally 覆盖 flush）')

// 2) 未知事件 / 非法 JSON
assert.equal(parseStreamEvent({ event: 'heartbeat', data: '{}' }), null, '未知事件应被忽略')
const broken = parseStreamEvent({ event: 'delta', data: 'not-json' })
assert.ok(broken && broken.kind === 'delta', '非法 JSON 仍按 delta 处理，只是 data 为空')
assert.equal(applyStreamEvent(initialStreamState, broken).text, '', '空 data 不得产生 undefined 文本')

// process 缺 seq 时忽略，避免出现 NaN 键
assert.equal(
  applyStreamEvent(initialStreamState, parseStreamEvent(sse('process', { kind: 'TOOL' }))).process.length,
  0,
)

// 3) delta 合批：三次 push 只 emit 一次，flush 后清空
const emitted = []
const batcher = createDeltaBatcher((chunk) => emitted.push(chunk), 10)
batcher.push('a')
batcher.push('b')
batcher.push('c')
assert.equal(emitted.length, 0, '未到刷新点前不得逐个 emit')
batcher.flush()
assert.deepEqual(emitted, ['abc'], '合批后的文本必须完整且只 emit 一次')
batcher.flush()
assert.deepEqual(emitted, ['abc'], '重复 flush 不得重复 emit')

console.log('streamEvents: 18 项断言全部通过')
