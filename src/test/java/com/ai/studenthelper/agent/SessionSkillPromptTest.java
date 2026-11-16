package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.MemoryInjectionService;
import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.application.SkillApplicationService;
import com.ai.studenthelper.application.SubjectSkillRuntime;
import com.ai.studenthelper.domain.enums.GuideMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CR-038 / AC-505-3~5：固化技能正文段、追问同段与关闭态逐字一致。 */
class SessionSkillPromptTest {

    private final MemoryInjectionService memoryInjection = mock(MemoryInjectionService.class);
    private final SkillApplicationService skills = mock(SkillApplicationService.class);
    private final S2FeatureService features = mock(S2FeatureService.class);

    private final List<String[]> fixed = List.of(
            new String[]{"复杂度标注", "每个算法都标注时间与空间复杂度。"},
            new String[]{"通俗讲解", "先讲直觉再给公式。"});

    @BeforeEach
    void setUp() {
        when(memoryInjection.block()).thenReturn("");
        when(skills.catalog()).thenReturn(List.of());
    }

    private S2PromptAugmenter augmenter() {
        return new S2PromptAugmenter(memoryInjection, skills, features);
    }

    @Test
    void sessionSkillBlockIsEmptyForNoSkillsAndCarriesBodies() {
        assertEquals("", SolverPromptTemplate.sessionSkillBlock(List.of()));
        String block = SolverPromptTemplate.sessionSkillBlock(fixed);
        assertTrue(block.contains("【本会话固定技能】"));
        assertTrue(block.contains("## 技能：复杂度标注"));
        assertTrue(block.contains("先讲直觉再给公式。"));
        assertTrue(block.contains("每一轮都必须沿用"));
    }

    /** CR-038 修正：固化只额外注入正文，CR-026 的按需清单必须同时保留。 */
    @Test
    void fixedSkillsCoexistWithOnDemandCatalog() {
        when(skills.catalog()).thenReturn(List.<String[]>of(new String[]{"甲", "说明"}));
        String prompt = augmenter().buildAndAugment(GuideMode.FULL, SubjectSkillRuntime.off(1L), fixed);
        assertTrue(prompt.contains("【本会话固定技能】"));
        assertTrue(prompt.contains("每个算法都标注时间与空间复杂度。"));
        assertTrue(prompt.contains("【可用技能（按需读入）】"), "按需清单没有被关掉");
        assertTrue(prompt.contains("名称=甲"));
        assertTrue(prompt.contains("无需重复读入"));
        verify(skills, atLeastOnce()).catalog();
    }

    @Test
    void unfixedStateReturnsVerbatimToCr026Catalog() {
        when(skills.catalog()).thenReturn(List.<String[]>of(new String[]{"甲", "说明"}));
        String prompt = augmenter().buildAndAugment(GuideMode.FULL, SubjectSkillRuntime.off(1L));
        assertTrue(prompt.contains("【可用技能（按需读入）】"));
        assertTrue(prompt.contains("名称=甲"));
        assertFalse(prompt.contains("每一轮都必须沿用"), "未固化时不出现正文段");
        assertEquals(prompt, augmenter().buildAndAugment(
                GuideMode.FULL, SubjectSkillRuntime.off(1L), List.of()));
    }

    @Test
    void followUpAppendsExactlyTheSameBlock() {
        String block = SolverPromptTemplate.sessionSkillBlock(fixed);
        String followUp = SolverPromptTemplate.followUp(null, null, GuideMode.FULL);
        String prompt = followUp + block;
        assertTrue(prompt.startsWith(followUp), "追问模板本身不改");
        assertTrue(prompt.contains("【本轮形态】自由问答"));
        assertFalse(followUp.contains("【本会话固定技能】"));
        assertEquals(block, prompt.substring(followUp.length()), "追加的是同一段正文");
    }

    @Test
    void closedSwitchStillReturnsBaseVerbatim() {
        String base = SolverPromptTemplate.build(GuideMode.FULL);
        assertSame(base, augmenter().augment(base));
        assertEquals(base, augmenter().buildAndAugment(GuideMode.FULL));
    }
}
