package com.ai.studenthelper.agent;

import com.ai.studenthelper.domain.enums.GuideMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SolverPromptTemplateTest {

    @Test
    void fullPromptContainsAllSixFixedTitlesInOrder() {
        String prompt = SolverPromptTemplate.build(GuideMode.FULL);
        int previous = -1;
        for (String title : SolverPromptTemplate.FULL_TITLES) {
            int position = prompt.indexOf(title);
            assertTrue(position > previous, "标题缺失或乱序：" + title);
            previous = position;
        }
        assertTrue(prompt.contains("不编造算法性质"));
        assertTrue(prompt.contains("只回答最后一次提问"));
        assertTrue(prompt.contains("遵循用户本轮对难度与讲解深度的要求"));
    }

    @Test
    void ideaOnlyPromptForbidsStepsAndFinalAnswer() {
        String prompt = SolverPromptTemplate.build(GuideMode.IDEA_ONLY);
        assertEquals(3, SolverPromptTemplate.titlesFor(GuideMode.IDEA_ONLY).size());
        assertTrue(prompt.contains("严禁"));
        assertFalse(prompt.contains("## ④ 详细步骤"));
        assertFalse(prompt.contains("## ⑤ 最终答案"));
    }

    @Test
    void promptForbidsSeventhSectionJsonAndDisclaimer() {
        String prompt = SolverPromptTemplate.build(GuideMode.FULL);
        assertTrue(prompt.contains("不要输出 ⑦ 举一反三内容"));
        assertTrue(prompt.contains("不要用 JSON 包裹答案"));
        assertTrue(prompt.contains("不要输出免责声明段落"));
        // CR-019：旧学科退役，首行改为学科通用称呼，不再出现具体课程名
        assertTrue(prompt.contains(SolverPromptTemplate.GENERIC_SUBJECT));
        assertFalse(prompt.contains("数据结构"));
        assertFalse(prompt.contains("课程的解题助手"));
    }

    /** CR-025：知识点段按当轮结构的第 2 段标题生成；结构不足 2 段时不注入。 */
    @Test
    void knowledgeBlockFollowsSecondSectionTitle() {
        String math = """
                {"full":["## ① 题意与已知条件","## ② 定理与公式","## ③ 推导过程"],
                 "idea":["## ① 题意与已知条件","## ② 定理与公式"]}
                """;
        String block = SolverPromptTemplate.knowledgeBlock(
                com.ai.studenthelper.common.SectionSchema.parse(math), GuideMode.FULL);
        assertTrue(block.contains("「## ② 定理与公式」"));
        assertTrue(block.contains("最多 3 行"));

        String single = """
                {"full":["## ① 唯一一段"],"idea":["## ① 唯一一段"]}
                """;
        assertEquals("", SolverPromptTemplate.knowledgeBlock(
                com.ai.studenthelper.common.SectionSchema.parse(single), GuideMode.FULL));
    }
}
