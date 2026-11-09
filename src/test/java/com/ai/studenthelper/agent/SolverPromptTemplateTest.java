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
        assertTrue(prompt.contains("数据结构"));
    }
}
