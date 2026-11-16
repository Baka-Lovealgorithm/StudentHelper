package com.ai.studenthelper.application;

import com.ai.studenthelper.domain.enums.S2Feature;

import java.util.Map;

/**
 * 请求级功能开关快照（CR-043 / 候选 05）。
 *
 * <p>改造前每次 {@code isEnabled} 都打一次 {@code t_feature_toggle}，一次问答链路累计约 12 次；
 * 同一开关还可能被判两次（Prompt 要求写知识点、抽取侧又判一次），中途翻转会出现口径不一致。</p>
 *
 * <p>快照在请求入口读一次、随调用链传递：一次请求内所有判断看到同一份状态，
 * 且读库次数与调用点数无关。写操作仍走 {@code requireEnabled}（每次现读，保证越权写立即生效）。</p>
 */
public record FeatureSnapshot(Map<String, Boolean> flags) {

    public static FeatureSnapshot empty() {
        return new FeatureSnapshot(Map.of());
    }

    public boolean enabled(S2Feature feature) {
        return feature != null && Boolean.TRUE.equals(flags.get(feature.key()));
    }

    public boolean enabled(String key) {
        return key != null && Boolean.TRUE.equals(flags.get(key));
    }
}
