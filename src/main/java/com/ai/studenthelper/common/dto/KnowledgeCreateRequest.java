package com.ai.studenthelper.common.dto;

/**
 * CR-040：手动新增知识点请求。
 *
 * <p>{@code subject} 与 {@code name} 必填；{@code parentName} 可空（表示单级知识点，
 * 落库为空串以适配唯一键）。</p>
 */
public record KnowledgeCreateRequest(
        String subject,
        String parentName,
        String name) {
}
