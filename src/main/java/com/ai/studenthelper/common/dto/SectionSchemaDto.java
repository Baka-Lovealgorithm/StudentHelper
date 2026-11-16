package com.ai.studenthelper.common.dto;

import java.util.List;

/** CR-018 / CR-039：sections_json 的 JSON 形状；explain 为可选的讲解结构，缺省回落默认五段。 */
public record SectionSchemaDto(List<String> full, List<String> idea, List<String> explain) {

    /** 兼容 CR-018 的两段构造：讲解结构缺省。 */
    public SectionSchemaDto(List<String> full, List<String> idea) {
        this(full, idea, null);
    }
}
