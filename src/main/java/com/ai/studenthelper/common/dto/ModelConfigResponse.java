package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/** 模型配置响应：绝不回显 apiKey，只给 hasApiKey（AC-105-3）。 */
public record ModelConfigResponse(
        Long id,
        String name,
        String provider,
        String baseUrl,
        String modelName,
        Boolean visionSupported,
        Boolean streamSupported,
        String extraOptions,
        Boolean isCurrent,
        Boolean hasApiKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
