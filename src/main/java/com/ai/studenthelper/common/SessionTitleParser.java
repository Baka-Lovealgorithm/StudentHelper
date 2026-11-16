package com.ai.studenthelper.common;

/**
 * CR-033：从模型回答中解析「会话标题」标记行并剥离（T-104 / AC-104-12~13）。
 *
 * <p>首轮解题时 Prompt 会要求模型以独立一行输出 {@code 【会话标题】xxx}（≤16 字）。
 * 解析规则：</p>
 * <ul>
 *   <li>按行扫描，首个匹配 {@code ^【会话标题】[:：]?\s*(.+)$} 的行即为标题行，
 *       无论其在回答开头、中间还是末尾；</li>
 *   <li>标题清洗：去掉首尾引号 / 括号 / 句末标点、压缩连续空白、trim，超过 16 字截断；</li>
 *   <li>清洗后为空或全为符号视为失败（返回 {@code null} 标题），调用方保留「新会话」；</li>
 *   <li>剥离时同时删除该行及其后的换行，保证落库正文与历史复制口径不含标记。</li>
 * </ul>
 * <p>剥离不拦截流式 delta，TTFT 口径不变；前端渲染层用同一正则过滤（utils/sessionTitle.ts）。</p>
 */
public final class SessionTitleParser {

    /** 标题最大长度（字符），超出截断。 */
    public static final int MAX_TITLE_CHARS = 16;
    /** 标记行前缀，与 Prompt 指令、前端 utils/sessionTitle.ts 保持逐字一致。 */
    public static final String MARKER = "【会话标题】";

    private SessionTitleParser() {
    }

    /** 解析结果：{@code title == null} 表示生成失败；{@code stripped} 为剥离后的正文。 */
    public record Result(String title, String stripped) {
    }

    /** 从回答中提取标题并剥离标记行；没有标记行时 title 为 null、正文原样返回。 */
    public static Result extract(String answer) {
        String text = answer == null ? "" : answer;
        String[] lines = text.split("\n", -1);
        StringBuilder stripped = new StringBuilder(text.length());
        String title = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (title == null) {
                String candidate = parseTitleLine(line);
                if (candidate != null) {
                    title = candidate;
                    continue;
                }
            }
            if (i > 0) {
                stripped.append('\n');
            }
            stripped.append(line);
        }
        // 剥离标记行后去掉开头多余空行，正文其余部分逐字保留
        String body = stripped.toString();
        if (title != null) {
            body = body.replaceFirst("^\\s*\\n", "");
        }
        return new Result(title, body);
    }

    /** 单行是否为标题行；是则返回清洗后的标题，否则返回 null。 */
    static String parseTitleLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith(MARKER)) {
            return null;
        }
        String rest = trimmed.substring(MARKER.length());
        if (rest.startsWith("：")) {
            rest = rest.substring(1);
        } else if (rest.startsWith(":")) {
            rest = rest.substring(1);
        }
        return sanitize(rest);
    }

    /** 清洗标题：去首尾引号 / 括号 / 句末标点、压缩空白、截断；空或全符号视为失败。 */
    static String sanitize(String raw) {
        String value = raw == null ? "" : raw.trim();
        value = value.replaceAll("\\s+", " ");
        // 引号 / 括号与句末标点可能交替出现（如 “标题”。），循环剥离直到稳定
        String previous = null;
        while (!value.equals(previous)) {
            previous = value;
            value = stripPair(value, '“', '”');
            value = stripPair(value, '『', '』');
            value = stripPair(value, '「', '」');
            value = stripPair(value, '"', '"');
            value = stripPair(value, '《', '》');
            value = stripPair(value, '（', '）');
            value = stripPair(value, '(', ')');
            value = value.replaceAll("[。，、；：！？.．,;:!？\\s]+$", "").trim();
        }
        if (value.length() > MAX_TITLE_CHARS) {
            value = value.substring(0, MAX_TITLE_CHARS);
        }
        // 全为符号 / 空视为失败：要求至少含一个文字或数字字符
        if (value.isEmpty() || !value.codePoints().anyMatch(SessionTitleParser::isTextChar)) {
            return null;
        }
        return value;
    }

    private static String stripPair(String value, char open, char close) {
        String result = value;
        while (result.length() >= 2 && result.charAt(0) == open
                && result.charAt(result.length() - 1) == close) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    private static boolean isTextChar(int codePoint) {
        return Character.isLetterOrDigit(codePoint);
    }
}
