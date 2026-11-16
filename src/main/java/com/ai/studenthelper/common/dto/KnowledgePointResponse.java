package com.ai.studenthelper.common.dto;

/**
 * 知识点聚合结果。
 *
 * <p>CR-040：掌握度退役，移除 {@code mastery} / {@code masteryLabel}；新增 {@code createdAt}
 * （{@code yyyy-MM-dd HH:mm}）供列表核对日期筛选的命中范围。</p>
 */
public record KnowledgePointResponse(
        Long id,
        String subject,
        String parentName,
        String name,
        int hitCount,
        String createdAt) {
}
