package com.ai.studenthelper.common.dto;

/** CR-012 会话级模型选择：只影响当前会话。 */
public record SessionModelRequest(Long modelId) {
}
