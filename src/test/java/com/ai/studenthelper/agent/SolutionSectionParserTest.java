package com.ai.studenthelper.agent;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.enums.GuideMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SolutionSectionParserTest {

    private final SolutionSectionParser parser = new SolutionSectionParser();

    private static String fullAnswer() {
        return "## ① 题目理解\n这是一道二叉树遍历题。\n"
                + "## ② 涉及知识点\n二叉树、递归。\n"
                + "## ③ 解题思路\n先定位根节点。\n"
                + "## ④ 详细步骤\n1. 从前序取根\n2. 在中序切分左右子树\n"
                + "## ⑤ 最终答案\n可唯一确定。\n"
                + "## ⑥ 易错点 / 验算\n注意重复元素。\n";
    }

    @Test
    void fullAnswerHasSixNonEmptySectionsInOrder() {
        var result = parser.parse(fullAnswer(), GuideMode.FULL);
        assertEquals(6, result.sections().size());
        assertEquals("## ① 题目理解", result.sections().get(0).title());
        assertEquals("## ⑥ 易错点 / 验算", result.sections().get(5).title());
        assertTrue(result.sections().stream().allMatch(s -> !s.body().isBlank()));
    }

    @Test
    void missingSectionThrowsE2005() {
        String broken = fullAnswer().replace("## ⑤ 最终答案\n可唯一确定。\n", "");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse(broken, GuideMode.FULL));
        assertEquals(ErrorCode.E2005, ex.getErrorCode());
    }

    @Test
    void emptySectionThrowsE2005() {
        String broken = fullAnswer().replace("二叉树、递归。", "   ");
        assertThrows(BusinessException.class, () -> parser.parse(broken, GuideMode.FULL));
    }

    @Test
    void ideaOnlyModeRequiresFirstThreeSections() {
        String answer = fullAnswer().substring(0, fullAnswer().indexOf("## ④ 详细步骤"));
        assertEquals(3, parser.parse(answer, GuideMode.IDEA_ONLY).sections().size());
        assertThrows(BusinessException.class, () -> parser.parse(answer, GuideMode.FULL));
    }

    @Test
    void disclaimerIsAppendedExactlyOnce() {
        String withLine = parser.appendDisclaimer(fullAnswer());
        assertTrue(withLine.endsWith("本内容由 AI 生成，请自行核对\n"));
        assertFalse(fullAnswer().contains("本内容由 AI 生成"));
    }

    @Test
    void disclaimerFromModelIsDeduplicated() {
        String modelEchoed = fullAnswer() + "\n本内容由 AI 生成，请自行核对\n";
        String stored = parser.appendDisclaimer(modelEchoed);
        assertEquals(1, countOccurrences(stored, "本内容由 AI 生成"));
        assertTrue(stored.endsWith("本内容由 AI 生成，请自行核对\n"));
    }

    @Test
    void appendedTwiceStaysSingleLine() {
        String once = parser.appendDisclaimer(fullAnswer());
        String twice = parser.appendDisclaimer(once);
        assertEquals(1, countOccurrences(twice, "本内容由 AI 生成"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
