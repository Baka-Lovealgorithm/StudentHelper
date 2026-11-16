package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.MemoryInjectionService;
import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.application.SkillApplicationService;
import com.ai.studenthelper.application.SubjectSkillRuntime;
import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.common.SubjectSpecJson;
import com.ai.studenthelper.common.dto.SubjectSkillResponse;
import com.ai.studenthelper.domain.enums.AnswerIntent;
import com.ai.studenthelper.domain.enums.GuideMode;
import com.ai.studenthelper.domain.enums.S2Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CR-018 / CR-039：三态注入、三段规范按段覆盖、系统默认兜底、硬约束位置。 */
class S2PromptAugmenterSubjectTest {

    private static final String MATH_JSON = """
            {"full":["## ① 题意与已知条件","## ② 定理与公式","## ③ 推导过程","## ④ 结论与验证","## ⑤ 易错点与适用边界"],
             "idea":["## ① 题意与已知条件","## ② 定理与公式","## ③ 思路要点"]}
            """;

    private final MemoryInjectionService memoryInjection = mock(MemoryInjectionService.class);
    private final SkillApplicationService skills = mock(SkillApplicationService.class);
    private final S2FeatureService features = mock(S2FeatureService.class);

    private final SubjectSkillResponse ds = new SubjectSkillResponse(1L, "DATA_STRUCTURE",
            "数据结构", "术语与复杂度口径", "BUILTIN", true, "栈、队列", "SPEC_DS",
            SectionSchema.defaultSchema().toJson(), LocalDateTime.now(), LocalDateTime.now(),
            SubjectSpecJson.toJson(new SubjectSpecJson.Specs("SPEC_DS", "", "")));
    private final SubjectSkillResponse math = new SubjectSkillResponse(2L, "ADVANCED_MATH",
            "高等数学", "推导型题目规范", "BUILTIN", true, "极限、积分", "SPEC_MATH",
            MATH_JSON, LocalDateTime.now(), LocalDateTime.now(),
            SubjectSpecJson.toJson(new SubjectSpecJson.Specs("SPEC_MATH", "FOLLOW_MATH", "EXPLAIN_MATH")));

    @BeforeEach
    void setUp() {
        when(memoryInjection.block()).thenReturn("");
        when(skills.catalog()).thenReturn(List.of());
        when(features.isEnabled(S2Feature.MULTI_SOLUTION)).thenReturn(false);
        when(features.isEnabled(S2Feature.MCP)).thenReturn(false);
    }

    private S2PromptAugmenter augmenter() {
        return new S2PromptAugmenter(memoryInjection, skills, features);
    }

    /** CR-039：subjectSkill 开关已移除；会话 OFF 时用系统默认规范，且不注入学科清单。 */
    @Test
    void offUsesSystemDefaultSpecWithoutCatalog() {
        String prompt = augmenter().buildAndAugment(AnswerIntent.SOLVE, GuideMode.FULL,
                SubjectSkillRuntime.off(1L), List.of());
        assertTrue(prompt.contains("【回答规范：系统默认 · 解题】"));
        assertTrue(prompt.contains(SolverPromptTemplate.DEFAULT_SOLVE_SPEC.lines().findFirst().orElse("")));
        assertFalse(prompt.contains("【学科规范（模型自选）】"));
        for (String title : SolverPromptTemplate.FULL_TITLES) {
            assertTrue(prompt.contains(title), "OFF 仍带默认六段：" + title);
        }
    }

    @Test
    void autoInjectsCatalogAndToolInstructionButNotSpecFullText() {
        String prompt = augmenter().buildAndAugment(AnswerIntent.SOLVE, GuideMode.FULL,
                SubjectSkillRuntime.auto(1L, List.of(ds, math)), List.of());
        assertTrue(prompt.contains("【学科规范（模型自选）】"));
        assertTrue(prompt.contains("subject_get(code)"));
        assertTrue(prompt.contains("DATA_STRUCTURE"));
        assertTrue(prompt.contains("ADVANCED_MATH"));
        assertFalse(prompt.contains("SPEC_MATH"), "AUTO 不注入规范全文，由工具按需读入");
        assertTrue(prompt.contains("【回答规范：系统默认 · 解题】"));
    }

    /** 三段规范：解题轮只取 solve 段，空段回落默认。 */
    @Test
    void manualInjectsSubjectSolveSpecReplacingDefault() {
        String prompt = augmenter().buildAndAugment(AnswerIntent.SOLVE, GuideMode.FULL,
                SubjectSkillRuntime.fixed(1L, SubjectSkillRuntime.Mode.MANUAL, math), List.of());
        assertTrue(prompt.startsWith("你是「" + SolverPromptTemplate.GENERIC_SUBJECT + "」解题助手"));
        assertTrue(prompt.contains("【回答规范：高等数学 · 解题】"));
        assertTrue(prompt.contains("SPEC_MATH"));
        assertTrue(prompt.contains("## ⑤ 易错点与适用边界"));
        assertFalse(prompt.contains("【回答规范：系统默认 · 解题】"), "非空段整段替换默认");
        assertFalse(prompt.contains("【学科规范（模型自选）】"));
        assertFalse(prompt.contains("## ④ 详细步骤"), "高数五段不应混入六段的 ④");

        String dsPrompt = augmenter().buildAndAugment(AnswerIntent.SOLVE, GuideMode.FULL,
                SubjectSkillRuntime.fixed(1L, SubjectSkillRuntime.Mode.MANUAL, ds), List.of());
        assertTrue(dsPrompt.contains("【回答规范：数据结构 · 解题】"));
        assertTrue(dsPrompt.contains("SPEC_DS"));
    }

    /** 追问轮取 followUp 段；留空时回落系统默认追问规范。 */
    @Test
    void followUpUsesSubjectFollowUpSpecOrSystemDefault() {
        String withSpec = augmenter().buildAndAugment(AnswerIntent.FOLLOW_UP, GuideMode.FULL,
                SubjectSkillRuntime.fixed(2L, SubjectSkillRuntime.Mode.MANUAL, math), List.of());
        assertTrue(withSpec.contains("【回答规范：高等数学 · 追问】"));
        assertTrue(withSpec.contains("FOLLOW_MATH"));
        assertFalse(withSpec.contains("SPEC_MATH"), "追问轮不注入解题规范");
        assertFalse(withSpec.contains("## ① 题意与已知条件"), "追问不套任何分段结构");

        String fallback = augmenter().buildAndAugment(AnswerIntent.FOLLOW_UP, GuideMode.FULL,
                SubjectSkillRuntime.fixed(1L, SubjectSkillRuntime.Mode.MANUAL, ds), List.of());
        assertTrue(fallback.contains("【回答规范：数据结构 · 追问】"));
        assertTrue(fallback.contains("【本轮形态】自由问答"));
        assertTrue(fallback.contains("直接、连贯地回答"));
    }

    /** 讲解轮取 explain 段并按 explain 数组输出结构。 */
    @Test
    void explainUsesExplainSpecAndDefaultTitles() {
        String prompt = augmenter().buildAndAugment(AnswerIntent.EXPLAIN, GuideMode.FULL,
                SubjectSkillRuntime.fixed(2L, SubjectSkillRuntime.Mode.MANUAL, math), List.of());
        assertTrue(prompt.contains("【回答规范：高等数学 · 讲解】"));
        assertTrue(prompt.contains("EXPLAIN_MATH"));
        assertFalse(prompt.contains("SPEC_MATH"));
        for (String title : SectionSchema.DEFAULT_EXPLAIN) {
            assertTrue(prompt.contains(title), "讲解轮默认五段：" + title);
        }
        assertFalse(prompt.contains("## ④ 详细步骤"), "讲解轮不带解题六段");

        String fallback = augmenter().buildAndAugment(AnswerIntent.EXPLAIN, GuideMode.FULL,
                SubjectSkillRuntime.off(1L), List.of());
        assertTrue(fallback.contains("【回答规范：系统默认 · 讲解】"));
    }

    /**
     * CR-039 原判定为「硬约束在规范段之后」；CR-065 把通用硬约束前置到公共头，
     * 位置改到规范段之前，但「学科规范整段替换默认规范时无法取消」的语义保持：
     * 公共头在规范段之外，自定义规范无论写什么都不会覆盖它。
     */
    @Test
    void commonHeaderPrecedesSpecBlockAndCannotBeOverridden() {
        String prompt = augmenter().buildAndAugment(AnswerIntent.SOLVE, GuideMode.FULL,
                SubjectSkillRuntime.fixed(1L, SubjectSkillRuntime.Mode.MANUAL, math), List.of());
        int specAt = prompt.indexOf("【回答规范：高等数学 · 解题】");
        int hardAt = prompt.indexOf("【不确定时】");
        int forbidAt = prompt.indexOf("不要用 JSON 包裹答案");
        assertTrue(specAt >= 0 && hardAt >= 0 && hardAt < specAt, "CR-065：硬约束位于规范段之前的公共头");
        assertTrue(forbidAt >= 0 && forbidAt < specAt);
        assertTrue(prompt.startsWith(SolverPromptTemplate.commonHeader()));
    }

    /** CR-065：三态共享逐字一致的公共头（形态分叉点后移）。 */
    @Test
    void threeIntentsShareVerbatimCommonHeader() {
        String header = SolverPromptTemplate.commonHeader();
        for (AnswerIntent intent : AnswerIntent.values()) {
            String prompt = augmenter().buildAndAugment(intent, GuideMode.FULL,
                    SubjectSkillRuntime.off(1L), List.of());
            assertTrue(prompt.startsWith(header), intent + " 应以公共头开头");
        }
        // 形态段紧随公共头，且三态各不相同
        String solve = SolverPromptTemplate.buildForIntent(AnswerIntent.SOLVE, GuideMode.FULL,
                null, null, null).substring(header.length());
        String followUp = SolverPromptTemplate.buildForIntent(AnswerIntent.FOLLOW_UP, GuideMode.FULL,
                null, null, null).substring(header.length());
        String explain = SolverPromptTemplate.buildForIntent(AnswerIntent.EXPLAIN, GuideMode.FULL,
                null, null, null).substring(header.length());
        assertNotEquals(solve, followUp);
        assertNotEquals(solve, explain);
        assertNotEquals(followUp, explain);
        assertFalse(solve.contains("【通用硬约束"), "公共头内容不在形态段重复");
    }

    @Test
    void subjectBlockPrecedesGeneralSkillCatalog() {
        when(skills.catalog()).thenReturn(List.<String[]>of(
                new String[]{"复杂度标注", "算法题显式标注复杂度"}));
        String prompt = augmenter().buildAndAugment(AnswerIntent.SOLVE, GuideMode.FULL,
                SubjectSkillRuntime.fixed(1L, SubjectSkillRuntime.Mode.MANUAL, math), List.of());
        assertTrue(prompt.indexOf("【回答规范：高等数学 · 解题】") < prompt.indexOf("【可用技能（按需读入）】"));
        assertTrue(prompt.contains("名称=复杂度标注"));
        assertTrue(prompt.contains("一次最多 2 条"));
        assertFalse(prompt.contains("## 技能："));
    }

    @Test
    void multiSolutionBlockOnlyAppliesToSolveIntent() {
        when(features.isEnabled(S2Feature.MULTI_SOLUTION)).thenReturn(true);
        assertTrue(SolverPromptTemplate.multiSolutionBlock(SectionSchema.parse(MATH_JSON))
                .contains("## ⑤ 易错点与适用边界」之后追加标题"));
        String solve = augmenter().buildAndAugment(AnswerIntent.SOLVE, GuideMode.FULL,
                SubjectSkillRuntime.fixed(2L, SubjectSkillRuntime.Mode.MANUAL, math), List.of());
        assertTrue(solve.contains("【多解法要求】"));
        String explain = augmenter().buildAndAugment(AnswerIntent.EXPLAIN, GuideMode.FULL,
                SubjectSkillRuntime.fixed(2L, SubjectSkillRuntime.Mode.MANUAL, math), List.of());
        assertFalse(explain.contains("【多解法要求】"), "讲解轮不追加多解法");
        assertFalse(explain.contains("## 补充："), "CR-024：补充段落已退役");
    }
}
