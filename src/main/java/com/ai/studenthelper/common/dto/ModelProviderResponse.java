package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;
import java.util.List;

/** CR-012 通用厂商配置响应：绝不回显 apiKey，只给 hasApiKey。 */
public record ModelProviderResponse(
        Long id,
        String name,
        String baseUrl,
        boolean hasApiKey,
        List<ProviderModelResponse> models,
        LocalDateTime updatedAt
) {
}
