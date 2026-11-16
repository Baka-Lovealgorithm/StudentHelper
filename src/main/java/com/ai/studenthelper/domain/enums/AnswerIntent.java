package com.ai.studenthelper.domain.enums;

import java.util.Locale;

/**
 * CR-039：本轮回答形态，由 {@code IntentClassifier} 在装配上下文之前判定。
 *
 * <p>三态并列：{@code SOLVE} 解题、{@code FOLLOW_UP} 追问、{@code EXPLAIN} 知识点讲解。
 * 判定失败、超时、模型未配置或输出不可解析时一律回落 {@code SOLVE}，绝不阻断解题。</p>
 */
public enum AnswerIntent {

    SOLVE("解题"),
    FOLLOW_UP("追问"),
    EXPLAIN("知识点讲解");

    private final String label;

    AnswerIntent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 容错解析：取首个出现的合法词；非法 / 空 → SOLVE。 */
    public static AnswerIntent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SOLVE;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("FOLLOW") || upper.contains("FOLLOW_UP")) {
            return FOLLOW_UP;
        }
        if (upper.startsWith("EXPLAIN") || upper.contains("EXPLAIN")) {
            return EXPLAIN;
        }
        return SOLVE;
    }

    /** 是否按题目解答口径处理（决定分段结构、仅思路裁剪、知识点抽取）。 */
    public boolean structured() {
        return this != FOLLOW_UP;
    }
}
