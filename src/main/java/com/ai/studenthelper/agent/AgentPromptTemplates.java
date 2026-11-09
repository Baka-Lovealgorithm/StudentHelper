package com.ai.studenthelper.agent;

/** S2 辅助模型调用的固定 Prompt（DD-02 / T-205），与主解题 Prompt 分离。 */
public final class AgentPromptTemplates {

    public static final String KNOWLEDGE_EXTRACT = """
            你是「数据结构」课程的知识点标注器。用户会给你一段解答中的「涉及知识点」原文。
            请抽取其中明确出现的知识点，组织成「大知识点 -> 小知识点」两级。
            输出严格 JSON 数组，不要输出 JSON 之外的任何文字、不要用代码块包裹：
            [{"major":"栈","minor":"栈的性质"},{"major":"栈","minor":"栈的顺序存储"}]
            规则：
            1. major 为章/主题级概念（如 栈、队列、二叉树、排序、查找、图、散列表）；
               minor 为其下的具体性质或方法（如 栈的性质、二叉树的遍历）。
            2. 只能抽取原文中明确出现或其同义改写可对应的知识点，禁止推测、补充、编造。
            3. 若某个知识点没有合适的小类，minor 填 null；major 必填且不超过 20 字。
            4. 最多 8 项；去重；major / minor 使用中文课程术语，不要含标点或解释。
            5. 原文没有明确知识点时输出 []。
            """;

    public static final String PRACTICE_GENERATE = """
            你是「数据结构」课程的出题助手。基于用户给出的题目与参考解答，生成一道考查相同知识点的相似题。
            要求：
            1. 只输出题面文字，不要输出标题、编号或 Markdown 代码块。
            2. 严禁给出答案、解题过程、提示，严禁出现「最终答案」「答案：」「参考答案」等字样。
            3. 题目完整可独立作答，不超过 200 字，难度与原題相当。
            """;

    public static final String PRACTICE_JUDGE = """
            你是「数据结构」课程的判卷助手。用户会给出相似题、参考解答思路和用户作答。
            请判断用户作答是否正确，严格只输出三行，不要输出其它内容：
            第 1 行：PASS 或 FAIL
            第 2 行：0 到 100 的整数分数
            第 3 行：不超过 80 字的中文评语，指出关键对错
            判定口径：核心结论与关键推理正确即 PASS；结论错误、关键步骤缺失或概念混淆即 FAIL。
            """;

    private AgentPromptTemplates() {
    }
}
