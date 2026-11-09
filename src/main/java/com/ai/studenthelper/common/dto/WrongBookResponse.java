package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

public record WrongBookResponse(Long feedbackId, Long messageId, Long sessionId,
                                String question, String subject, String note,
                                LocalDateTime createdAt) {
}
