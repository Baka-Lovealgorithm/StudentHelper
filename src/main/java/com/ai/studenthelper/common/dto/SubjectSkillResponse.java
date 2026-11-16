package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/** CR-018：学科规范条目；CR-039 起带三段规范 `specJson`；CR-060 起不再区分内置 / 自定义。 */
public record SubjectSkillResponse(
        Long id,
        String code,
        String name,
        String description,
        String scope,
        boolean enabled,
        String keywords,
        String specMd,
        String sectionsJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String specJson
) {
    /** 兼容 CR-018 的 11 参构造：没有三段规范时按旧单段 `specMd` 处理。 */
    public SubjectSkillResponse(Long id, String code, String name, String description, String scope,
                                boolean enabled, String keywords, String specMd, String sectionsJson,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, code, name, description, scope, enabled, keywords, specMd, sectionsJson,
                createdAt, updatedAt, null);
    }
}
