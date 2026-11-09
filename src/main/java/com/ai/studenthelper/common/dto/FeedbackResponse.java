package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/** 标记记录回显（FR-207 / T-207）。 */
public record FeedbackResponse(
        Long id,
        Long messageId,
        Long sessionId,
        String kind,
        String subject,
        String note,
        LocalDateTime createdAt
) {
}
