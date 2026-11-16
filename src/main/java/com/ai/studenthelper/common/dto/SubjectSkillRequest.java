package com.ai.studenthelper.common.dto;

/**
 * CR-018：自定义学科规范的表单录入 / 编辑请求。
 *
 * <p>CR-039 起新增 {@code specJson}（{"solve","followUp","explain"}）；{@code specMd} 保留为
 * 解题规范 {@code solve} 的兼容入参，两者同时给出时以 {@code specJson} 为准。</p>
 */
public record SubjectSkillRequest(
        String name,
        String description,
        String keywords,
        String specMd,
        String sectionsJson,
        Boolean enabled,
        String specJson
) {
    /** 兼容 CR-018 的 6 参调用（等价于只填解题规范）。 */
    public SubjectSkillRequest(String name, String description, String keywords,
                               String specMd, String sectionsJson, Boolean enabled) {
        this(name, description, keywords, specMd, sectionsJson, enabled, null);
    }
}
