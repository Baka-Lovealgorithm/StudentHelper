package com.ai.studenthelper.common.dto;

import java.util.List;

/** CR-012 配置页列表：厂商清单 + 全局默认模型 id。 */
public record ProviderListResponse(List<ModelProviderResponse> providers, Long defaultModelId) {
}
