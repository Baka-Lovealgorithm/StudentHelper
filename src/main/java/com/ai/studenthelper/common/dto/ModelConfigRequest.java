package com.ai.studenthelper.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 模型配置请求；apiKey 留空表示「不修改」（编辑时）。 */
public record ModelConfigRequest(
        @NotBlank String name,
        @NotBlank String provider,
        @NotBlank String baseUrl,
        String apiKey,
        @NotBlank String modelName,
        @NotNull Boolean visionSupported,
        Boolean streamSupported,
        String extraOptions
) {
}
