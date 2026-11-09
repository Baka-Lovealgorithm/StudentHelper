package com.ai.studenthelper.common.dto;

/** 标记请求（FR-207 / T-207）。kind：WRONG / INCORRECT / REGENERATE。 */
public record FeedbackRequest(Long messageId, String kind, String subject, String note) {
}
