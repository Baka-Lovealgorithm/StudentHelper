package com.ai.studenthelper.common.dto;

import java.util.List;

/**
 * CR-014 用量看板响应：按模型聚合行 + 按天分桶 + 当前窗口（0 = 全部）。
 * 不做金额换算（FR-206 边界），不返回原始日志明细（AC-111-3）。
 */
public record UsageSummaryResult(
        List<UsageSummaryResponse> rows,
        List<UsageDailyResponse> daily,
        int days
) {
}
