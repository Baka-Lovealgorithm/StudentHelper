package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/** 健康检查响应体。 */
public record HealthResponse(String status, String app, LocalDateTime time) {
}
