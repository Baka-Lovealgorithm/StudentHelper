package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/**
 * 标记记录回显。
 *
 * <p>CR-041 起反馈闭环（{@code FR-207}）已退役，本 DTO 仅供错题本（{@code FR-202} / {@code T-202}）
 * 使用——错题本以 {@code kind = 'WRONG'} 复用 {@code t_message_feedback}，返回类型不变。</p>
 */
public record FeedbackResponse(
        Long id,
        Long messageId,
        Long sessionId,
        String kind,
        String subject,
        String note,
        LocalDateTime createdAt
) {
}
