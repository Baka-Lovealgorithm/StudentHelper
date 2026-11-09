package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.OptionalSectionService;
import com.ai.studenthelper.application.ProfileApplicationService;
import com.ai.studenthelper.common.dto.OptionalSectionsResponse;
import com.ai.studenthelper.domain.enums.GuideMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class S2PromptAugmenterTest {

    private final ProfileApplicationService profile = mock(ProfileApplicationService.class);
    private final OptionalSectionService sections = mock(OptionalSectionService.class);

    @Test
    void closedStateReturnsBaseVerbatim() {
        when(profile.enabledMemorySnippets()).thenReturn(List.of());
        when(sections.current()).thenReturn(new OptionalSectionsResponse(false, false, false));
        String base = SolverPromptTemplate.build(GuideMode.FULL);
        assertSame(base, new S2PromptAugmenter(profile, sections).augment(base));
        assertEquals(SolverPromptTemplate.build(GuideMode.IDEA_ONLY),
                new S2PromptAugmenter(profile, sections)
                        .augment(SolverPromptTemplate.build(GuideMode.IDEA_ONLY)));
    }

    @Test
    void openStateAppendsMemoriesAndSupplementalTitles() {
        when(profile.enabledMemorySnippets()).thenReturn(List.of("讲解通俗一些"));
        when(sections.current()).thenReturn(new OptionalSectionsResponse(false, false, true));
        String base = SolverPromptTemplate.build(GuideMode.FULL);
        String result = new S2PromptAugmenter(profile, sections).augment(base);
        assertTrue(result.startsWith(base));
        assertTrue(result.contains("讲解通俗一些"));
        assertTrue(result.contains("## 补充：参考代码"));
        assertFalse(result.contains("## 补充：思路详解"));
        assertFalse(result.contains("## ⑦"));
    }

    @Test
    void supplementalSectionsDoNotBreakSixSectionParsing() {
        String withSupplement = SolverPromptTemplate.build(GuideMode.FULL)
                + "\n## 补充：参考代码\n```java\nint x = 1;\n```\n";
        // parse 只校验标题存在性，用真实六段文本拼接补充段
        StringBuilder text = new StringBuilder();
        for (String title : SolverPromptTemplate.FULL_TITLES) {
            text.append(title).append("\n内容\n");
        }
        text.append("\n## 补充：参考代码\n```java\nint x = 1;\n```\n");
        assertDoesNotThrow(() -> new SolutionSectionParser().parse(text.toString(), GuideMode.FULL));
        assertNotNull(withSupplement);
    }
}
