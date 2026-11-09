package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.OptionalSectionService;
import com.ai.studenthelper.application.ProfileApplicationService;
import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.application.SkillApplicationService;
import com.ai.studenthelper.application.SubjectApplicationService;
import com.ai.studenthelper.common.dto.OptionalSectionsResponse;
import com.ai.studenthelper.domain.enums.GuideMode;
import com.ai.studenthelper.domain.enums.S2Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class S2PromptAugmenterTest {

    private final ProfileApplicationService profile = mock(ProfileApplicationService.class);
    private final OptionalSectionService sections = mock(OptionalSectionService.class);
    private final SubjectApplicationService subjects = mock(SubjectApplicationService.class);
    private final SkillApplicationService skills = mock(SkillApplicationService.class);
    private final S2FeatureService features = mock(S2FeatureService.class);

    @BeforeEach
    void setUp() {
        when(profile.enabledMemorySnippets()).thenReturn(List.of());
        when(sections.current()).thenReturn(new OptionalSectionsResponse(false, false, false));
        when(skills.enabledSnippets()).thenReturn(List.of());
        when(features.isEnabled(S2Feature.MULTI_SOLUTION)).thenReturn(false);
        when(subjects.currentForPrompt())
                .thenReturn(new SubjectApplicationService.SubjectContext("数据结构", null));
    }

    private S2PromptAugmenter augmenter() {
        return new S2PromptAugmenter(profile, sections, subjects, skills, features);
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
    void openStateAppendsMemoriesAndSupplementalTitles() {
        when(profile.enabledMemorySnippets()).thenReturn(List.of("讲解通俗一些"));
        when(sections.current()).thenReturn(new OptionalSectionsResponse(false, false, true));
        String base = SolverPromptTemplate.build(GuideMode.FULL);
        String result = augmenter().augment(base);
        assertTrue(result.startsWith(base));
        assertTrue(result.contains("讲解通俗一些"));
        assertTrue(result.contains("## 补充：参考代码"));
        assertFalse(result.contains("## 补充：思路详解"));
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

    @Test
    void subjectSwitchRewritesFirstLineAndAppendsHint() {
        when(subjects.currentForPrompt()).thenReturn(
                new SubjectApplicationService.SubjectContext("高等数学", "关键变形写出定理名"));
        String result = augmenter().buildAndAugment(GuideMode.FULL);
        assertTrue(result.startsWith("你是「高等数学」课程的解题助手"));
        assertTrue(result.contains("【学科补充】关键变形写出定理名"));
        assertTrue(result.contains("②使用高等数学课程术语"));
        assertFalse(result.contains("「数据结构」"));
    }

    @Test
    void skillBlockIsInjectedWhenEnabled() {
        when(skills.enabledSnippets()).thenReturn(List.<String[]>of(
                new String[]{"复杂度标注", "每个算法都显式标注时间与空间复杂度。"}));
        String result = augmenter().buildAndAugment(GuideMode.FULL);
        assertTrue(result.contains("【已加载技能】"));
        assertTrue(result.contains("## 技能：复杂度标注"));
        assertTrue(result.contains("显式标注时间与空间复杂度"));
    }
}
