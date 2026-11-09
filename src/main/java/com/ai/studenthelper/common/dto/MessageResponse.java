package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/** 消息响应；不返回内部用量与日志字段之外的敏感信息。 */
public record MessageResponse(
        Long id,
        String role,
        String content,
        String hint,
        String imagePaths,
        String status,
        String errorCode,
        LocalDateTime createdAt
) {
}
