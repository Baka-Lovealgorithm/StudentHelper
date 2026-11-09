package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/** 会话响应。 */
public record SessionResponse(
        Long id,
        String title,
        Integer guideMode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
