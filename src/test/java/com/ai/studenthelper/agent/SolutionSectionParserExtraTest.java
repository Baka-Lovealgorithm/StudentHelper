package com.ai.studenthelper.agent;

import com.ai.studenthelper.domain.enums.GuideMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** T-306：一题多解追加「## 附加：」后，六段校验不受影响。 */
class SolutionSectionParserExtraTest {

    private final SolutionSectionParser parser = new SolutionSectionParser();

    private static String answerWithExtra() {
        return "## ① 题目理解\n题干。\n"
                + "## ② 涉及知识点\n栈。\n"
                + "## ③ 解题思路\n两种思路。\n"
                + "## ④ 详细步骤\n1. 甲\n2. 乙\n"
                + "## ⑤ 最终答案\n答案。\n"
                + "## ⑥ 易错点 / 验算\n注意边界。\n"
                + "\n## 附加：多解法对比\n解法一：递归。\n解法二：迭代。\n差异：空间复杂度不同。\n";
    }

    @Test
    void extraMultiSolutionSectionDoesNotBreakParsing() {
        var result = assertDoesNotThrow(() ->
                parser.parse(answerWithExtra(), GuideMode.FULL));
        assertEquals(6, result.sections().size());
        assertEquals("## ⑥ 易错点 / 验算", result.sections().get(5).title());
    }
}
