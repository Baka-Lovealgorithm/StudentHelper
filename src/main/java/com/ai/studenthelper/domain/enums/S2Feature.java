package com.ai.studenthelper.domain.enums;

/**
 * S2 逐功能独立开关键（DD-01 / TCR-016）。全部默认关闭，由 t_feature_toggle 持久化。
 * key 与 upgrade-s2.1.sql 预置的 9 行一一对应。
 */
public enum S2Feature {
    USAGE_PANEL("usagePanel", "用量看板", "T-206"),
    FEEDBACK("feedback", "反馈闭环", "T-207"),
    MEMORY("memory", "长期记忆与画像", "T-201"),
    WRONG_BOOK("wrongBook", "错题本", "T-202"),
    KNOWLEDGE_EXTRACT("knowledgeExtract", "知识点回顾", "T-203"),
    OPTIONAL_SECTIONS("optionalSections", "可选输出段落", "T-204"),
    PRACTICE("practice", "举一反三", "T-205"),
    WORKSPACE("workspace", "文件工作区", "T-208"),
    STEP_UNLOCK("stepUnlock", "分步解锁", "T-209");

    private final String key;
    private final String label;
    private final String task;

    S2Feature(String key, String label, String task) {
        this.key = key;
        this.label = label;
        this.task = task;
    }

    public String key() { return key; }
    public String label() { return label; }
    public String task() { return task; }

    public static S2Feature fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (S2Feature feature : values()) {
            if (feature.key.equals(key)) {
                return feature;
            }
        }
        return null;
    }
}
