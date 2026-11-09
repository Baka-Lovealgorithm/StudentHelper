package com.ai.studenthelper.common.dto;

import java.util.List;

/** 解题请求命令（应用层，不含 HTTP 协议细节）。 */
public record ChatCommand(
        Long sessionId,
        String text,
        String hint,
        int guideMode,
        List<ImagePayload> images
) {
    public ChatCommand {
        images = images == null ? List.of() : images;
    }
}
