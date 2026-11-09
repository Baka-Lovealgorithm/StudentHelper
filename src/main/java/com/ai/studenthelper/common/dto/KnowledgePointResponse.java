package com.ai.studenthelper.common.dto;

import java.math.BigDecimal;

/** 知识点聚合结果。mastery 为 null 时 masteryLabel 固定为「未评估」（AC-203-3）。 */
public record KnowledgePointResponse(
        Long id,
        String subject,
        String parentName,
        String name,
        int hitCount,
        BigDecimal mastery,
        String masteryLabel) {
}
