package com.ai.studenthelper.common.dto;

import java.util.List;

/** CR-012 对话页两级下拉选项。 */
public record ProviderOptionsResponse(List<Option> options, Long defaultModelId) {

    public record Option(Long providerId, String providerName, List<Model> models) {
    }

    public record Model(Long id, String modelName) {
    }
}
