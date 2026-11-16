/**
 * CR-033：与会端 `SessionTitleParser` 同口径，剥离模型首轮回答中的【会话标题】行。
 * 后端落库前已剥离，历史正文通常不含标记；这里兜底流式期间（标记行随 delta 到达）
 * 与历史数据，保证正文与复制口径不含标记。
 */

export const SESSION_TITLE_MARKER = '【会话标题】'

/**
 * 剥离标题行。规则：
 * - 流式期间首行尚未出现换行：若开头已像标题行（完整或部分 marker），先返回空串，
 *   避免标记行闪现；调用方需以返回值是否为空决定是否渲染正文外壳（AC-502-8）。
 * - 首行（首个换行前）是标题行：剥离首行及其后空行。
 * - 首行不是标题行：原样返回（标题行只允许出现在第一行，其它位置由后端剥离兜底）。
 */
export function stripSessionTitle(text: string, streaming = false): string {
  const value = text ?? ''
  const trimmed = value.trimStart()
  const newline = value.indexOf('\n')
  if (newline < 0) {
    if (streaming && trimmed.length > 0
      && (trimmed.startsWith(SESSION_TITLE_MARKER)
        || (trimmed.startsWith('【') && SESSION_TITLE_MARKER.startsWith(trimmed)))) {
      return ''
    }
    return value
  }
  const firstLine = value.slice(0, newline)
  if (!firstLine.trim().startsWith(SESSION_TITLE_MARKER)) {
    return value
  }
  return value.slice(newline + 1).replace(/^\s*\n/, '')
}
