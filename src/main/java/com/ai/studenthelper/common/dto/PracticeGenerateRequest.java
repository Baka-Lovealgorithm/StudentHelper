package com.ai.studenthelper.common.dto;

/** 生成相似题：sourceMessageId 必填，kpId 可空。 */
public record PracticeGenerateRequest(Long sourceMessageId, Long kpId) {
}
