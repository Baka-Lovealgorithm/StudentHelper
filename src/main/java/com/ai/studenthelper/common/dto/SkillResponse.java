package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/** T-302 解题技能。 */
public record SkillResponse(Long id, String name, String description, String content,
                            boolean enabled, LocalDateTime updatedAt) {
}
