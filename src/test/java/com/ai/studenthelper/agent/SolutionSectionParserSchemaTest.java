package com.ai.studenthelper.agent;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.domain.enums.GuideMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** CR-018 / AC-501-7 / AC-501-8：按 sections_json 切段、仅思路按 idea、默认六段不回归。 */
class SolutionSectionParserSchemaTest {

    private static final String MATH_JSON = """
            {"full":["## ① 题意与已知条件","## ② 定理与公式","## ③ 推导过程","## ④ 结论与验证","## ⑤ 易错点与适用边界"],
             "idea":["## ① 题意与已知条件","## ② 定理与公式","## ③ 思路要点"]}
            """;

    private static final String MATH_FULL = """
            ## ① 题意与已知条件
            求 x→0 时的极限，x 为实数。
            ## ② 定理与公式
            使用重要极限 lim(x→0) sin x / x = 1。
            ## ③ 推导过程
            第一步，把原式拆为两项；第二步，分别代入重要极限。
            ## ④ 结论与验证
            极限为 1，回代 x=0.01 数值近似一致。
            ## ⑤ 易错点与适用边界
            注意该极限只在 x→0 时成立。
            """;

    private static final String MATH_IDEA = """
            ## ① 题意与已知条件
            求 x→0 时的极限。
            ## ② 定理与公式
            重要极限 lim(x→0) sin x / x = 1。
            ## ③ 思路要点
            先拆项再代入，不写最终数值。
            """;

    private final SolutionSectionParser parser = new SolutionSectionParser();

    @Test
    void mathFullSchemaParsesFiveSectionsInOrder() {
        var result = parser.parse(MATH_FULL, GuideMode.FULL, SectionSchema.parse(MATH_JSON));
        assertEquals(5, result.sections().size());
        assertEquals("## ① 题意与已知条件", result.sections().get(0).title());
        assertEquals("## ⑤ 易错点与适用边界", result.sections().get(4).title());
        assertTrue(result.sections().get(3).body().contains("极限为 1"));
    }

    @Test
    void mathIdeaSchemaParsesThreeSections() {
        var result = parser.parse(MATH_IDEA, GuideMode.IDEA_ONLY, SectionSchema.parse(MATH_JSON));
        assertEquals(3, result.sections().size());
        assertEquals("## ③ 思路要点", result.sections().get(2).title());
    }

    @Test
    void mathSchemaRejectsMissingSectionWithE2005() {
        String missing = MATH_FULL.replace("## ④ 结论与验证", "## 随便一段");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> parser.parse(missing, GuideMode.FULL, SectionSchema.parse(MATH_JSON)));
        assertEquals(ErrorCode.E2005, ex.getErrorCode());
    }

    @Test
    void defaultSchemaStillValidatesSixSections() {
        String six = """
                ## ① 题目理解
                题意。
                ## ② 涉及知识点
                知识点。
                ## ③ 解题思路
                思路。
                ## ④ 详细步骤
                第一步；第二步。
                ## ⑤ 最终答案
                答案。
                ## ⑥ 易错点 / 验算
                验算。
                """;
        assertEquals(6, parser.parse(six, GuideMode.FULL).sections().size());
        assertEquals(6, parser.parse(six, GuideMode.FULL, SectionSchema.defaultSchema()).sections().size());
    }

    @Test
    void supplementalTitlesAfterLastSectionDoNotBreakParsing() {
        String withExtra = MATH_FULL + "\n## 补充：参考代码\n```text\n占位\n```\n";
        var result = parser.parse(withExtra, GuideMode.FULL, SectionSchema.parse(MATH_JSON));
        assertEquals(5, result.sections().size());
        assertTrue(result.sections().get(4).body().contains("注意该极限"));
        assertFalse(result.sections().get(4).body().contains("补充：参考代码"));
    }
}
