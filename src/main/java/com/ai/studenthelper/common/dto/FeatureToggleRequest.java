package com.ai.studenthelper.common.dto;

import jakarta.validation.constraints.NotNull;

/** 更新单个 S2 功能开关。 */
public record FeatureToggleRequest(@NotNull Boolean enabled) {
}
