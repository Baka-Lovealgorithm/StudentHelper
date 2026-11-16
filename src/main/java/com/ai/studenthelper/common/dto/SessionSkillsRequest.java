package com.ai.studenthelper.common.dto;

import java.util.List;

/**
 * CR-038：会话级通用技能固化；空数组 / null 表示清除固化、回到模型按需读入。
 * 最多 2 条，且 id 必须存在并启用（由 Application 层校验）。
 */
public record SessionSkillsRequest(List<Long> skillIds) {
}
