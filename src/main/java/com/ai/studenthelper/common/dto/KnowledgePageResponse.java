package com.ai.studenthelper.common.dto;

import java.util.List;

/**
 * CR-040：知识点分页结果。与 {@link SessionPageResponse} 同构——只提供「更多」所需的
 * {@code total} / {@code hasMore}，不提供页码跳转。
 */
public record KnowledgePageResponse(
        List<KnowledgePointResponse> items,
        long total,
        boolean hasMore
) {
}
