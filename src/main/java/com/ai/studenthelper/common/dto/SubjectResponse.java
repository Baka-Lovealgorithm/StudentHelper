package com.ai.studenthelper.common.dto;

import java.time.LocalDateTime;

/** T-307 学科模板（`prompt_hint` 为学科补充说明，可为空）。 */
public record SubjectResponse(Long id, String name, String promptHint, boolean current,
                              LocalDateTime createdAt) {
}
