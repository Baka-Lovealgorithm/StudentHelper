package com.ai.studenthelper.common;

/**
 * TCR-040 / AC-401-9：token 估算。
 *
 * <p>项目未引入 tokenizer，按 {@code token ≈ ceil(字符数 / 1.5)} 估算（中文 1 token ≈ 1.5 字符，
 * 与 DeepSeek 中文切分的经验值一致）。仅用于「是否该压缩上下文」这种量级判断，
 * 不参与计费或精确预算核算，因此不追求与真实分词器逐 token 对齐。</p>
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    /** 空串与 null 记为 0 token。 */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // ceil(chars / 1.5) 等价于 ceil(2 * chars / 3)，写成 (2n + 2) / 3 全程整数运算，避免浮点误差
        return (text.length() * 2 + 2) / 3;
    }

    /** 多条文本累加；null 元素跳过。 */
    public static int estimateAll(Iterable<String> texts) {
        if (texts == null) {
            return 0;
        }
        int total = 0;
        for (String text : texts) {
            total += estimate(text);
        }
        return total;
    }
}
