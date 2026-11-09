package com.ai.studenthelper.common.dto;

/** CR-012 通用厂商配置入参：只填名称 / Base URL / Key；apiKey 留空表示不修改。 */
public record ProviderRequest(String name, String baseUrl, String apiKey) {
}
