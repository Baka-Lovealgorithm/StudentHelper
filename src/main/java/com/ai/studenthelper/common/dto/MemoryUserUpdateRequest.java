package com.ai.studenthelper.common.dto;

/** T-401：更新 MEMORY.md 用户手写区；version 用于乐观锁。 */
public record MemoryUserUpdateRequest(String content, String version) {
}
