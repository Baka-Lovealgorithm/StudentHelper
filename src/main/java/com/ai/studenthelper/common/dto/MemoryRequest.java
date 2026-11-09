package com.ai.studenthelper.common.dto;

public record MemoryRequest(String kind, String content, Long sourceSessionId) {
}
