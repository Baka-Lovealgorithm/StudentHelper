package com.ai.studenthelper.application;

import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.domain.enums.AnswerIntent;
import com.ai.studenthelper.domain.enums.GuideMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** CR-043 / AC-508-1~3：本轮形态决策收敛到 AnswerPlan，下游只读字段。 */
class AnswerPlanTest {

    private static SubjectSkillRuntime runtime(SectionSchema... schemas) {
        SubjectSkillRuntime runtime = mock(SubjectSkillRuntime.class);
        if (schemas.length == 0) {
            when(runtime.schemaFor(org.mockito.ArgumentMatchers.any())).thenReturn(null);
            when(runtime.resolved()).thenReturn(null);
            when(runtime.needsTools()).thenReturn(true);
        } else if (schemas.length == 1) {
            when(runtime.schemaFor(org.mockito.ArgumentMatchers.any())).thenReturn(schemas[0]);
        } else {
            when(runtime.schemaFor(org.mockito.ArgumentMatchers.any())).thenReturn(schemas[0], schemas[1]);
        }
        return runtime;
    }

    /** 追问：自由问答，不注册工具、不抽知识点、落库 FREE_TEXT、无标签。 */
    @Test
    void followUpHasNoStructureAndNoTools() {
        AnswerPlan plan = AnswerPlan.build(AnswerIntent.FOLLOW_UP, GuideMode.FULL, "prompt",
                true, true, true, runtime());

        assertTrue(plan.followUp());
        assertFalse(plan.extractKnowledge());
        assertFalse(plan.needsSubjectTools(), "追问不注册学科工具");
        assertFalse(plan.needsSkillTools(), "追问不注册技能工具");
        assertFalse(plan.autoTitle(), "追问不追加会话标题指令");
        assertEquals(3, plan.maxIters(), "无按需工具时迭代上限回到 3");
        assertEquals(SectionSchema.FREE_TEXT, plan.storedSchema(null));
        assertNull(plan.subjectLabel(null));
        assertNull(plan.currentSchema(), "追问无分段结构");
        assertFalse(plan.parseTitles(null).iterator().hasNext());
    }

    /** 讲解：按 explain 五段切、抽知识点关闭、不做仅思路裁剪、默认结构落哨兵。 */
    @Test
    void explainUsesExplainTitlesAndDoesNotTrim() {
        AnswerPlan plan = AnswerPlan.build(AnswerIntent.EXPLAIN, GuideMode.IDEA_ONLY, "prompt",
                true, true, false, runtime());

        assertFalse(plan.extractKnowledge(), "讲解轮不抽知识点");
        assertFalse(plan.trimOnIdeaOnly(), "讲解不按仅思路裁剪");
        assertNull(plan.cutTitle());
        assertEquals(SectionSchema.DEFAULT_EXPLAIN, plan.parseTitles(plan.currentSchema()));
        assertEquals(SectionSchema.EXPLAIN, plan.storedSchema(plan.currentSchema()));
        // CR-047：结构化轮另注册 diagram_render，基础 3 + 学科 1 + 技能 1 + 作图 1
        assertEquals(6, plan.maxIters(), "学科 + 技能 + 作图三组工具各 +1");
    }

    /** 解题 + 仅思路：切断点取默认结构的 ④，且落库不写结构 JSON（默认结构）。 */
    @Test
    void solveIdeaOnlyCutsAtFourthSection() {
        AnswerPlan plan = AnswerPlan.build(AnswerIntent.SOLVE, GuideMode.IDEA_ONLY, "prompt",
                true, false, false, runtime());

        assertTrue(plan.trimOnIdeaOnly());
        assertEquals("## ④ 详细步骤", plan.cutTitle());
        assertTrue(plan.extractKnowledge());
        assertNull(plan.storedSchema(plan.currentSchema()), "默认结构不落 JSON，历史渲染口径不变");
        // CR-047：学科 + 作图两组工具 → 3 + 1 + 1
        assertEquals(5, plan.maxIters());
    }

    /**
     * 缺陷修复验证：AUTO 模式下模型通过 subject_get 换成自定义结构后，
     * 切断点必须跟着变——改造前用生成前的快照，会按默认 ④ 去裁，导致漏裁。
     */
    @Test
    void cutPointFollowsSchemaResolvedDuringGeneration() {
        SectionSchema custom = SectionSchema.parse("""
                {"full":["## ① 题意","## ② 方法","## ③ 推导","## ④ 结论"],
                 "idea":["## ① 题意","## ② 方法"]}
                """);
        SubjectSkillRuntime runtime = mock(SubjectSkillRuntime.class);
        when(runtime.schemaFor(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null)              // 生成前：AUTO 尚未命中
                .thenReturn(custom);           // subject_get 命中后

        AnswerPlan plan = AnswerPlan.build(AnswerIntent.SOLVE, GuideMode.IDEA_ONLY, "prompt",
                true, false, false, runtime);

        assertEquals("## ④ 详细步骤", plan.cutTitle(), "未命中时按默认结构裁剪（与 S1 一致）");
        assertEquals("## ③ 推导", plan.cutTitle(), "命中后按自定义结构的第一个被排除段裁剪");
    }

    /** 非默认学科结构且已命中：落库写结构 JSON，标签带学科名。 */
    @Test
    void customSchemaIsPersistedAndLabelled() {
        SectionSchema custom = SectionSchema.parse("""
                {"full":["## ① a","## ② b","## ③ c","## ④ d"],
                 "idea":["## ① a","## ② b"],
                 "explain":["## ① 定义","## ② 性质"]}
                """);
        SubjectSkillRuntime runtime = runtime(custom);
        when(runtime.resolved()).thenReturn(
                new com.ai.studenthelper.common.dto.SubjectSkillResponse(1L, "DS", "数据结构",
                        null, "BUILTIN", true, null, "spec", "{}", null, null, null));
        when(runtime.labelFor(AnswerIntent.SOLVE, GuideMode.FULL)).thenReturn("数据结构 · 4 段完整结构");

        AnswerPlan plan = AnswerPlan.build(AnswerIntent.SOLVE, GuideMode.FULL, "prompt",
                true, false, false, runtime);

        assertEquals("数据结构 · 4 段完整结构", plan.subjectLabel(plan.currentSchema()));
        assertTrue(plan.storedSchema(plan.currentSchema()).contains("## ① a"),
                "非默认结构落库 JSON，保证历史按同一结构渲染");
        assertEquals(List.of("## ① a", "## ② b", "## ③ c", "## ④ d"),
                plan.parseTitles(custom), "解题按引导模式取 full / idea");
    }
}
