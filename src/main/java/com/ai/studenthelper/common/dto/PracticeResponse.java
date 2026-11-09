package com.ai.studenthelper.common.dto;

import java.math.BigDecimal;

/** 举一反三练习记录（AC-205-2 四步状态可查）。 */
public record PracticeResponse(
        Long id,
        Long sourceMessageId,
        Long sessionId,
        Long kpId,
        String variantText,
        String userAnswer,
        String judgeResult,
        BigDecimal score,
        String feedback,
        String createdAt,
        String judgedAt) {
}
