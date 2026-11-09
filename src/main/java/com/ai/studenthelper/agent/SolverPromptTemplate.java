package com.ai.studenthelper.agent;

import com.ai.studenthelper.domain.enums.GuideMode;

import java.util.List;

/** 系统 Prompt：六段固定标题 + 风格/串题/难度约束（技术规范 4.2）。 */
public final class SolverPromptTemplate {

    public static final List<String> FULL_TITLES = List.of(
            "## ① 题目理解", "## ② 涉及知识点", "## ③ 解题思路",
            "## ④ 详细步骤", "## ⑤ 最终答案", "## ⑥ 易错点 / 验算");

    public static final List<String> IDEA_TITLES = List.of(
            "## ① 题目理解", "## ② 涉及知识点", "## ③ 解题思路");

    private SolverPromptTemplate() {
    }

    public static List<String> titlesFor(GuideMode mode) {
        return mode == GuideMode.IDEA_ONLY ? IDEA_TITLES : FULL_TITLES;
    }

    public static String build(GuideMode mode) {
        List<String> titles = titlesFor(mode);
        StringBuilder sb = new StringBuilder();
        sb.append("你是「数据结构」课程的解题助手，只用中文作答。\n\n");
        sb.append("【输出结构，标题必须逐字一致，顺序固定】\n");
        for (String title : titles) {
            sb.append(title).append('\n');
        }
        sb.append("每个标题下必须写非空内容；④详细步骤至少包含 2 个可区分的推导或操作单元。\n\n");
        sb.append("【风格】答案严谨、讲解易懂；不寒暄、不写与题目无关的内容、不标注教材章节；");
        sb.append("②使用数据结构课程术语，不使用通用聊天式寒暄。\n");
        sb.append("【串题约束】只回答最后一次提问，不引用与本题无关的上一题结论。\n");
        sb.append("【不确定时】不编造算法性质、复杂度或术语；不拒答、不给多个候选答案。\n");
        sb.append("【本轮要求】遵循用户本轮对难度与讲解深度的要求（该要求会附在本轮用户消息中）。\n");
        sb.append("【禁止】不要输出 ⑦ 举一反三内容、不要生成相似练习题、不要用 JSON 包裹答案、");
        sb.append("不要输出免责声明段落。\n");
        if (mode == GuideMode.IDEA_ONLY) {
            sb.append("【仅思路模式】只输出 ①②③，**严禁**输出 ④详细步骤 与 ⑤最终答案，也不得隐含给出最终答案。\n");
        }
        return sb.toString();
    }

    /** T-201：跨会话偏好的 Prompt 增量；仅 MEMORY 开关开启且有记忆时追加。 */
    public static String memoryBlock(List<String> memories) {
        StringBuilder block = new StringBuilder(
                "\n【用户长期偏好】（跨会话生效，优先级低于本轮题目与难度要求）\n");
        for (String memory : memories) {
            block.append("- ").append(memory).append('\n');
        }
        block.append("请在不违背题意的前提下贴合这些偏好，不要复述本段、不要据此编造题目信息。\n");
        return block.toString();
    }

    /** T-204：可选补充段落的 Prompt 增量；标题不带圈号，避开 ⑦ 禁用段。 */
    public static String optionalSectionBlock(boolean idea, boolean knowledge, boolean code) {
        StringBuilder block = new StringBuilder(
                "\n【补充段落】在 ⑥ 之后追加下列标题，标题必须逐字一致、每个标题下写非空内容；"
                        + "不得改动 ①~⑥ 的标题、顺序与内容：\n");
        if (idea) {
            block.append("## 补充：思路详解\n");
        }
        if (knowledge) {
            block.append("## 补充：知识点清单\n");
        }
        if (code) {
            block.append("## 补充：参考代码\n");
        }
        block.append("只输出上面列出的补充段落；代码块用 Markdown 围栏包裹、不要执行；"
                + "不要输出 ⑦ 举一反三内容。\n");
        return block.toString();
    }
}
