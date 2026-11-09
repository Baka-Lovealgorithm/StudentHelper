package com.ai.studenthelper.common.dto;

/** CR-012 厂商下的模型条目（视觉 / 流式 / 扩展参数均在模型级）。 */
public record ProviderModelResponse(
        Long id,
        Long providerId,
        String modelName,
        boolean visionSupported,
        boolean streamSupported,
        String extraOptions,
        boolean isDefault
) {
}
