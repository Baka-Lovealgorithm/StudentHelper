package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 会话响应；CR-018 追加会话级学科三态，CR-038 追加会话固化的通用技能 id 列表。 */
public record SessionResponse(
        Long id,
        String title,
        Integer guideMode,
        Long modelId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String subjectMode,
        Long subjectSkillId,
        List<Long> skillIds
) {
}
