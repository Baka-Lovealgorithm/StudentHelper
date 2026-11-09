package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

public record MemoryResponse(Long id, String kind, String content, Long sourceSessionId,
                             boolean enabled, LocalDateTime createdAt) {
}
