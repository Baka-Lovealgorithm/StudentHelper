package com.ai.studenthelper.common.dto;

/** S2 功能开关状态。 */
public record FeatureToggleResponse(String key, String label, String task, boolean enabled) {
}
