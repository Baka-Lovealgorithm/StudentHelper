package com.ai.studenthelper.common.dto;

/**
 * 按模型聚合的用量看板行（FR-206 / T-206）。
 * 只做次数、token 与耗时聚合，**不做金额换算**（SRS FR-206 边界）。
 */
public record UsageSummaryResponse(
        String modelName,
        long calls,
        long successCalls,
        long failedCalls,
        long promptTokens,
        long completionTokens,
        long avgDurationMs
) {
}
