package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.MemoryInjectionService;
import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.application.SkillApplicationService;
import com.ai.studenthelper.domain.enums.GuideMode;
import com.ai.studenthelper.domain.enums.S2Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class S2PromptAugmenterTest {

    private final MemoryInjectionService memoryInjection = mock(MemoryInjectionService.class);
    private final SkillApplicationService skills = mock(SkillApplicationService.class);
    private final S2FeatureService features = mock(S2FeatureService.class);

    @BeforeEach
    void setUp() {
        when(memoryInjection.block()).thenReturn("");
        when(skills.catalog()).thenReturn(List.of());
        when(features.isEnabled(S2Feature.MULTI_SOLUTION)).thenReturn(false);
    }

    /** CR-024：可选段落服务依赖移除，构造从 4 参变 3 参。 */
    private S2PromptAugmenter augmenter() {
        return new S2PromptAugmenter(memoryInjection, skills, features);
    }

    @Test
    void closedStateReturnsBaseVerbatim() {
        String base = SolverPromptTemplate.build(GuideMode.FULL);
        assertSame(base, augmenter().augment(base));
        assertEquals(SolverPromptTemplate.build(GuideMode.FULL),
                augmenter().buildAndAugment(GuideMode.FULL));
        assertEquals(SolverPromptTemplate.build(GuideMode.IDEA_ONLY),
                augmenter().buildAndAugment(GuideMode.IDEA_ONLY));
    }

    @Test
    void openStateAppendsLongTermMemoryOnly() {
        when(memoryInjection.block())
                .thenReturn(SolverPromptTemplate.longTermMemoryBlock("讲解通俗一些"));
        String base = SolverPromptTemplate.build(GuideMode.FULL);
        String result = augmenter().augment(base);
        assertTrue(result.startsWith(base));
        assertTrue(result.contains("【长期记忆】"));
        assertTrue(result.contains("讲解通俗一些"));
        // CR-024：可选输出段落已退役，任何「补充：」标题都不应再出现
        assertFalse(result.contains("## 补充："));
        assertFalse(result.contains("【补充段落】"));
        assertFalse(result.contains("## ⑦"));
    }

    @Test
    void multiSolutionAppendsBlockWithoutBreakingSixSections() {
        when(features.isEnabled(S2Feature.MULTI_SOLUTION)).thenReturn(true);
        String result = augmenter().buildAndAugment(GuideMode.FULL);
        assertTrue(result.contains("## 附加：多解法对比"));
        assertTrue(result.contains("解法一"));
        assertFalse(result.contains("## ⑦"));
        for (String title : SolverPromptTemplate.FULL_TITLES) {
            assertTrue(result.contains(title));
        }
    }

    /** CR-019 / AC-501-14：旧学科退役后首行恒为学科通用称呼，不再出现具体课程名。 */
    @Test
    void retiredSubjectUsesGenericTitle() {
        String result = augmenter().buildAndAugment(GuideMode.FULL);
        assertTrue(result.startsWith("你是「" + SolverPromptTemplate.GENERIC_SUBJECT + "」解题助手"));
        assertTrue(result.contains("②使用对应课程的规范术语"));
        assertFalse(result.contains("数据结构"));
        assertFalse(result.contains("高等数学"));
        assertFalse(result.contains("【学科补充】"));
        assertFalse(result.contains("课程的解题助手"));
    }

    /** CR-026：只注入清单（名称 / 说明），技能正文不再出现在 system prompt。 */
    @Test
    void skillCatalogIsInjectedWithoutBody() {
        when(skills.catalog()).thenReturn(List.<String[]>of(
                new String[]{"复杂度标注", "算法题显式标注复杂度"}));
        String result = augmenter().buildAndAugment(GuideMode.FULL);
        assertTrue(result.contains("【可用技能（按需读入）】"));
        assertTrue(result.contains("名称=复杂度标注"));
        assertTrue(result.contains("算法题显式标注复杂度"));
        assertTrue(result.contains("skill_get(name)"));
        assertFalse(result.contains("每个算法都显式标注时间与空间复杂度"));
    }

    /** CR-025：知识点开关开启时把第 2 段约束成「大 > 小」短列表，关闭时不出现。 */
    @Test
    void knowledgeBlockOnlyWhenEnabled() {
        String base = SolverPromptTemplate.build(GuideMode.FULL);
        String closed = augmenter().augment(base);
        assertFalse(closed.contains("【知识点】"));

        when(features.isEnabled(S2Feature.KNOWLEDGE_EXTRACT)).thenReturn(true);
        String opened = augmenter().augment(base);
        assertTrue(opened.contains("【知识点】第 2 段「## ② 涉及知识点」"));
        assertTrue(opened.contains("大知识点 > 小知识点"));
        assertTrue(opened.contains("最多 3 行"));
        // 其余增量与六段结构不受影响
        assertTrue(opened.startsWith(base));
        for (String title : SolverPromptTemplate.FULL_TITLES) {
            assertTrue(opened.contains(title));
        }
    }

    /** AC-401-2：MEMORY.md 整块作为一个增量，不额外拼出第二个记忆段。 */
    @Test
    void longTermMemoryBlockIsAppendedExactlyOnce() {
        String block = SolverPromptTemplate.longTermMemoryBlock("## 我的偏好\n- 讲通俗些");
        when(memoryInjection.block()).thenReturn(block);
        String base = SolverPromptTemplate.build(GuideMode.FULL);
        String result = augmenter().augment(base);
        assertTrue(result.startsWith(base));
        assertEquals(base.length() + block.length(), result.length());
        assertEquals(1, result.split("【长期记忆】", -1).length - 1);
    }
}
