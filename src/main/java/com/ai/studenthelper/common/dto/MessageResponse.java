package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/** 消息响应；CR-018 追加输出结构与学科标签，供前端按 schema 渲染。 */
public record MessageResponse(
        Long id,
        String role,
        String content,
        String hint,
        String imagePaths,
        String status,
        String errorCode,
        LocalDateTime createdAt,
        String sectionSchema,
        String subjectLabel,
        /** CR-039：本轮回答形态；历史消息为 null。 */
        String intent
) {
    /** 兼容 CR-038 及以前的 10 参构造。 */
    public MessageResponse(Long id, String role, String content, String hint, String imagePaths,
                           String status, String errorCode, java.time.LocalDateTime createdAt,
                           String sectionSchema, String subjectLabel) {
        this(id, role, content, hint, imagePaths, status, errorCode, createdAt, sectionSchema,
                subjectLabel, null);
    }
}
