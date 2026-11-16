package com.ai.studenthelper.common.dto;

/**
 * CR-027：一条模型行为过程记录。流式期间经 SSE `process` 推送（messageId 为 null），
 * 生成结束后按助手消息 id 落库，回读时带 messageId 供前端分组。
 */
public record ProcessItem(
        Long messageId,
        int seq,
        String kind,
        String title,
        String detail,
        long elapsedMs) {

    public static final String KIND_SUBJECT = "SUBJECT";
    public static final String KIND_SKILL = "SKILL";
    public static final String KIND_TOOL = "TOOL";
    public static final String KIND_THINKING = "THINKING";

    public ProcessItem withMessageId(Long id) {
        return new ProcessItem(id, seq, kind, title, detail, elapsedMs);
    }
}
