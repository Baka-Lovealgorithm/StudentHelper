package com.ai.studenthelper.common;

import com.ai.studenthelper.common.dto.SectionSchemaDto;
import com.ai.studenthelper.domain.enums.GuideMode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * CR-018：可插拔输出结构。默认结构恒等于 S1 的六段（full）与 ①②③（idea），
 * 保证未命中学科 / 会话 OFF / 历史 NULL 消息与 {@code AC-204-1} 逐字一致。
 *
 * <p>{@code FREE_TEXT} 是追问专用的哨兵值：表示该回答不套任何输出结构。
 * CR-039 起新增 {@code explain} 数组（知识点讲解结构）与其哨兵 {@code EXPLAIN}，
 * 旧 JSON 缺少 {@code explain} 时回落到 {@link #DEFAULT_EXPLAIN}，历史数据无需改写。</p>
 */
public final class SectionSchema {

    public static final String FREE_TEXT = "FREE_TEXT";

    /** CR-039：讲解轮使用默认讲解结构时的落库哨兵。 */
    public static final String EXPLAIN = "EXPLAIN";

    public static final List<String> DEFAULT_FULL = List.of(
            "## ① 题目理解", "## ② 涉及知识点", "## ③ 解题思路",
            "## ④ 详细步骤", "## ⑤ 最终答案", "## ⑥ 易错点 / 验算");
    public static final List<String> DEFAULT_IDEA = List.of(
            "## ① 题目理解", "## ② 涉及知识点", "## ③ 解题思路");
    /** CR-039：知识点讲解默认五段。 */
    public static final List<String> DEFAULT_EXPLAIN = List.of(
            "## ① 概念定义", "## ② 关键性质与适用条件", "## ③ 典型例子",
            "## ④ 与相邻概念的区别", "## ⑤ 常见误区");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TITLES = 12;
    private static final int MAX_TITLE_CHARS = 60;

    private final List<String> full;
    private final List<String> idea;
    private final List<String> explain;

    private SectionSchema(List<String> full, List<String> idea, List<String> explain) {
        this.full = List.copyOf(full);
        this.idea = List.copyOf(idea);
        this.explain = List.copyOf(explain);
    }

    public static SectionSchema defaultSchema() {
        return new SectionSchema(DEFAULT_FULL, DEFAULT_IDEA, DEFAULT_EXPLAIN);
    }

    /** 从 DB / 请求里的 sections_json 解析并校验；非法结构抛 E1004，不落库。 */
    public static SectionSchema parse(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "输出结构不能为空");
        }
        SectionSchemaDto dto;
        try {
            dto = MAPPER.readValue(json, SectionSchemaDto.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.E1004, "输出结构必须是含 full / idea 的 JSON 对象");
        }
        if (dto == null) {
            throw new BusinessException(ErrorCode.E1004, "输出结构必须是含 full / idea 的 JSON 对象");
        }
        List<String> explain = dto.explain() == null || dto.explain().isEmpty()
                ? DEFAULT_EXPLAIN : validateTitles(dto.explain(), "explain");
        return new SectionSchema(validateTitles(dto.full(), "full"),
                validateTitles(dto.idea(), "idea"), explain);
    }

    /** 用于会话级「已固化」读取：结构损坏时回退默认结构，绝不让历史消息解不开。 */
    public static SectionSchema parseOrNull(String json) {
        if (json == null || json.isBlank() || FREE_TEXT.equals(json) || EXPLAIN.equals(json)) {
            return null;
        }
        try {
            return parse(json);
        } catch (BusinessException e) {
            return null;
        }
    }

    private static List<String> validateTitles(List<String> raw, String field) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(ErrorCode.E1004, "输出结构 " + field + " 必须是非空有序标题数组");
        }
        if (raw.size() > MAX_TITLES) {
            throw new BusinessException(ErrorCode.E1004, "输出结构 " + field + " 最多 " + MAX_TITLES + " 段");
        }
        List<String> out = new ArrayList<>();
        for (String title : raw) {
            if (title == null || !title.startsWith("## ") || title.trim().length() <= 3) {
                throw new BusinessException(ErrorCode.E1004, "输出结构 " + field + " 的标题必须以 \"## \" 开头且非空");
            }
            String normalized = title.trim();
            if (normalized.length() > MAX_TITLE_CHARS) {
                throw new BusinessException(ErrorCode.E1004, "输出结构标题不能超过 " + MAX_TITLE_CHARS + " 字");
            }
            if (out.contains(normalized)) {
                throw new BusinessException(ErrorCode.E1004, "输出结构 " + field + " 存在重复标题：" + normalized);
            }
            out.add(normalized);
        }
        return out;
    }

    public List<String> titlesFor(GuideMode mode) {
        return mode == GuideMode.IDEA_ONLY ? idea : full;
    }

    /** CR-039：讲解轮固定用 explain 数组，不受引导模式影响。 */
    public List<String> explainTitles() {
        return explain == null || explain.isEmpty() ? DEFAULT_EXPLAIN : explain;
    }

    public List<String> full() { return full; }
    public List<String> idea() { return idea; }
    public List<String> explain() { return explain; }

    public boolean isDefault() {
        return full.equals(DEFAULT_FULL) && idea.equals(DEFAULT_IDEA);
    }

    /** CR-039：讲解结构是否等于默认五段。 */
    public boolean isDefaultExplain() {
        return explainTitles().equals(DEFAULT_EXPLAIN);
    }

    /** 仅思路模式后端强制截断的起点：full 中第一个不在 idea 里的标题。 */
    public String firstExcludedTitle() {
        for (String title : full) {
            if (!idea.contains(title)) {
                return title;
            }
        }
        return null;
    }

    /** 「本次采用」标签里的结构描述，例如「6 段完整结构」。 */
    public String labelFor(GuideMode mode) {
        return titlesFor(mode).size() + " 段" + (mode == GuideMode.IDEA_ONLY ? "仅思路结构" : "完整结构");
    }

    /** CR-039：讲解轮的结构描述。 */
    public String labelForExplain() {
        return explainTitles().size() + " 段讲解结构";
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(new SectionSchemaDto(full, idea, explain));
        } catch (Exception e) {
            throw new IllegalStateException("SECTION_SCHEMA_SERIALIZE_FAILED", e);
        }
    }
}
