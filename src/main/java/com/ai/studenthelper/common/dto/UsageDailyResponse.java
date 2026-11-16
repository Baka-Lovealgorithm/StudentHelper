package com.ai.studenthelper.common.dto;

/**
 * CR-014：按天分桶的用量小计（纯文本 / CSS 条形，不引入图表库）。
 * CR-065：增缓存命中 / 未命中 token 汇总，与按模型聚合同源可对账。
 */
public record UsageDailyResponse(
        String day,
        long calls,
        long successCalls,
        long failedCalls,
        long promptTokens,
        long completionTokens,
        long cacheHitTokens,
        long cacheMissTokens
) {
    /** 兼容既有调用（单测 / 桩）：缓存字段记 0。 */
    public UsageDailyResponse(String day, long calls, long successCalls, long failedCalls,
                              long promptTokens, long completionTokens) {
        this(day, calls, successCalls, failedCalls, promptTokens, completionTokens, 0L, 0L);
    }
}
