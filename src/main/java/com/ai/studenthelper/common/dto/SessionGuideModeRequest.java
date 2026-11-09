package com.ai.studenthelper.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 切换引导模式：0 完整解答 / 1 仅思路（FR-108）。 */
public record SessionGuideModeRequest(
        @NotNull @Min(0) @Max(1) Integer guideMode
) {
}
