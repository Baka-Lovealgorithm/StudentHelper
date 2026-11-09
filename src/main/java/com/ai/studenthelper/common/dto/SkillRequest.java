package com.ai.studenthelper.common.dto;

/** T-302 新增 / 修改技能的入参。`enabled` 省略时表示不修改。 */
public record SkillRequest(String name, String description, String content, Boolean enabled) {
}
