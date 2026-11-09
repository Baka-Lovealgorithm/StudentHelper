package com.ai.studenthelper.common.dto;

import java.util.List;

/** 会话分页结果；只提供「加载更多」所需的 total / hasMore，不提供页码跳转。 */
public record SessionPageResponse(
        List<SessionResponse> items,
        long total,
        boolean hasMore
) {
}
