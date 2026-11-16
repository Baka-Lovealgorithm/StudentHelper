package com.ai.studenthelper.domain.enums;

/**
 * 逐功能独立开关键（DD-01 / TCR-016，S3 起扩展为通用开关表）。全部默认关闭，由 t_feature_toggle 持久化。
 * CR-019 起 15 项，CR-024 退役 `optionalSections` 后为 14 项，CR-037 新增 `historyImage` 后为 15 项，
 * CR-039 移除 `subjectSkill` 后为 14 项（学科规范恒可用，不再由功能开关控制），
 * CR-041 退役 `feedback` 后为 13 项（标记有误 / 重新生成下线），
 * CR-042 退役 `workspace` 后为 12 项（文件工作区从未实现，空表 `t_workspace` 留档）；`t_feature_toggle` 中的
 * `subjectTemplate` 行（upgrade-s3.sql）仅作历史留档，不再对应任何枚举、不出现在功能开关列表、
 * 也不被任何代码读取。
 */
public enum S2Feature {
    USAGE_PANEL("usagePanel", "用量看板", "T-206"),
    MEMORY("memory", "长期记忆与画像", "T-201"),
    CONVERSATION_COMPACTION("conversationCompaction", "对话压缩", "T-401"),
    WRONG_BOOK("wrongBook", "错题本", "T-202"),
    KNOWLEDGE_EXTRACT("knowledgeExtract", "知识点回顾", "T-203"),
    PRACTICE("practice", "举一反三", "T-205"),
    STEP_UNLOCK("stepUnlock", "分步解锁", "T-209"),
    MULTI_SOLUTION("multiSolution", "一题多解", "T-306"),
    SKILL("skill", "技能加载", "T-302"),
    MCP("mcp", "MCP 工具", "T-301"),
    LEARNING_STATS("learningStats", "学情统计", "T-305"),
    /** CR-037 / T-504：历史图片入上下文 + 发给模型前尺寸压缩；默认开启。 */
    HISTORY_IMAGE("historyImage", "历史图片入上下文", "T-504");

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
