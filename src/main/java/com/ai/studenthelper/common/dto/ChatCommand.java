package com.ai.studenthelper.common.dto;

import java.util.List;

/** 解题请求命令（应用层，不含 HTTP 协议细节）。 */
public record ChatCommand(
        Long sessionId,
        String text,
        String hint,
        int guideMode,
        List<ImagePayload> images,
        List<ContentPart> parts,
        /** CR-066：举一反三出题轮标记——跳过意图分类、强制 FOLLOW_UP 并追加「只出题」约束。 */
        boolean practice
) {
    public ChatCommand {
        images = images == null ? List.of() : images;
        parts = parts == null ? List.of() : parts;
    }

    /** 兼容旧调用（无有序段）：文字整体作为唯一文本段。 */
    public ChatCommand(Long sessionId, String text, String hint, int guideMode, List<ImagePayload> images) {
        this(sessionId, text, hint, guideMode, images, List.of(), false);
    }

    /** 兼容旧调用（无 practice 标记）。 */
    public ChatCommand(Long sessionId, String text, String hint, int guideMode,
                       List<ImagePayload> images, List<ContentPart> parts) {
        this(sessionId, text, hint, guideMode, images, parts, false);
    }
}
