package com.ai.studenthelper.common.dto;

/** done 事件载荷；必须在消息与日志落库成功后才交给 sink。 */
public record DonePayload(
        Long messageId,
        int promptTokens,
        int completionTokens,
        long durationMs,
        long ttftMs
) {
}
