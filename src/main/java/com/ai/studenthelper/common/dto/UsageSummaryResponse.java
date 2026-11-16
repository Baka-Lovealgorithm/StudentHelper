package com.ai.studenthelper.common.dto;

/**
 * 按模型聚合的用量看板行（FR-206 / T-206）。
 * 只做次数、token 与耗时聚合，**不做金额换算**（SRS FR-206 边界）。
 * CR-065：增缓存命中 / 未命中 token，供前端计算命中率。
 */
public record UsageSummaryResponse(
        String modelName,
        long calls,
        long successCalls,
        long failedCalls,
        long promptTokens,
        long completionTokens,
        long avgDurationMs,
        long cacheHitTokens,
        long cacheMissTokens
) {
    /** 兼容既有调用（单测 / 桩）：缓存字段记 0。 */
    public UsageSummaryResponse(String modelName, long calls, long successCalls, long failedCalls,
                                long promptTokens, long completionTokens, long avgDurationMs) {
        this(modelName, calls, successCalls, failedCalls, promptTokens, completionTokens,
                avgDurationMs, 0L, 0L);
    }
}
