package com.ai.studenthelper.common;

import com.ai.studenthelper.domain.enums.GuideMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** CR-018 / AC-501-2 / AC-501-7：输出结构解析、校验与默认六段一致性。 */
class SectionSchemaTest {

    private static final String MATH_JSON = """
            {"full":["## ① 题意与已知条件","## ② 定理与公式","## ③ 推导过程","## ④ 结论与验证","## ⑤ 易错点与适用边界"],
             "idea":["## ① 题意与已知条件","## ② 定理与公式","## ③ 思路要点"]}
            """;

    @Test
    void defaultSchemaMatchesS1SixSectionsVerbatim() {
        SectionSchema schema = SectionSchema.defaultSchema();
        assertEquals(com.ai.studenthelper.agent.SolverPromptTemplate.FULL_TITLES, schema.full());
        assertEquals(com.ai.studenthelper.agent.SolverPromptTemplate.IDEA_TITLES, schema.idea());
        assertTrue(schema.isDefault());
        assertEquals("## ④ 详细步骤", schema.firstExcludedTitle());
        assertEquals("6 段完整结构", schema.labelFor(GuideMode.FULL));
    }

    @Test
    void mathSchemaHasFiveFullSectionsAndThreeIdeaSections() {
        SectionSchema schema = SectionSchema.parse(MATH_JSON);
        assertEquals(5, schema.full().size());
        assertEquals(3, schema.idea().size());
        assertFalse(schema.isDefault());
        // ③ 在 idea 里换成「思路要点」，因此 full 的第一个被排除段就是「③ 推导过程」
        assertEquals("## ③ 推导过程", schema.firstExcludedTitle());
        assertEquals("## ④ 结论与验证", schema.titlesFor(GuideMode.FULL).get(3));
        assertEquals("## ③ 思路要点", schema.titlesFor(GuideMode.IDEA_ONLY).get(2));
    }

    @Test
    void invalidSchemaIsRejectedWithE1004() {
        for (String bad : new String[]{
                null, "", "{}", "not json",
                "{\"full\":[],\"idea\":[\"## ① 题目理解\"]}",
                "{\"full\":[\"① 题目理解\"],\"idea\":[\"## ① 题目理解\"]}",
                "{\"full\":[\"## ① 题目理解\",\"## ① 题目理解\"],\"idea\":[\"## ① 题目理解\"]}",
                "{\"full\":[\"## ① 题目理解\"],\"idea\":[]}"}) {
            BusinessException ex = assertThrows(BusinessException.class, () -> SectionSchema.parse(bad));
            assertEquals(ErrorCode.E1004, ex.getErrorCode());
        }
    }

    @Test
    void freeTextSentinelAndCorruptHistoryFallBackToDefault() {
        assertNull(SectionSchema.parseOrNull(SectionSchema.FREE_TEXT));
        assertNull(SectionSchema.parseOrNull(null));
        assertNull(SectionSchema.parseOrNull("{broken"));
        assertEquals(SectionSchema.defaultSchema().full(),
                SectionSchema.parse(SectionSchema.defaultSchema().toJson()).full());
    }
}
