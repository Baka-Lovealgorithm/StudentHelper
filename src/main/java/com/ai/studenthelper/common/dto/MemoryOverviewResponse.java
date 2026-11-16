package com.ai.studenthelper.common.dto;

import java.util.List;

/** T-401：长期记忆面板概览。 */
public record MemoryOverviewResponse(
        String user,
        String model,
        String version,
        int injectMaxChars,
        List<MemoryFileInfo> files
) {
    public record MemoryFileInfo(String name, long size, String updatedAt) {
    }
}
