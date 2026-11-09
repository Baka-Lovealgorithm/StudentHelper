package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.SkillRequest;
import com.ai.studenthelper.common.dto.SkillResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.SkillRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillApplicationServiceTest {

    private final SkillRepository repository = mock(SkillRepository.class);
    private final S2FeatureService features = mock(S2FeatureService.class);
    private final SkillApplicationService service = new SkillApplicationService(repository, features);

    @Test
    void disabledStateInjectsNothing() {
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(false);
        assertTrue(service.enabledSnippets().isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void snippetsAreCappedAndTruncated() {
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(true);
        String longContent = "x".repeat(1200);
        when(repository.findEnabled(SkillApplicationService.MAX_INJECTED_SKILLS)).thenReturn(List.of(
                new SkillResponse(1L, "甲", null, longContent, true, LocalDateTime.now()),
                new SkillResponse(2L, "乙", null, "短内容", true, LocalDateTime.now()),
                new SkillResponse(3L, "丙", null, "第三条", true, LocalDateTime.now())));
        List<String[]> snippets = service.enabledSnippets();
        assertEquals(2, snippets.size());
        assertEquals(SkillApplicationService.MAX_INJECTED_CHARS, snippets.get(0)[1].length());
        assertEquals("乙", snippets.get(1)[0]);
    }

    @Test
    void emptyContentIsSkipped() {
        when(features.isEnabled(S2Feature.SKILL)).thenReturn(true);
        when(repository.findEnabled(SkillApplicationService.MAX_INJECTED_SKILLS)).thenReturn(List.of(
                new SkillResponse(1L, "空技能", null, "   ", true, LocalDateTime.now()),
                new SkillResponse(2L, "有内容", null, "按步骤写", true, LocalDateTime.now())));
        List<String[]> snippets = service.enabledSnippets();
        assertEquals(1, snippets.size());
        assertEquals("有内容", snippets.get(0)[0]);
    }

    @Test
    void emptyContentIsRejectedOnCreate() {
        doNothing().when(features).requireEnabled(S2Feature.SKILL);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new SkillRequest("技能", null, "  ", true)));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
    }

    @Test
    void duplicateNameIsRejected() {
        doNothing().when(features).requireEnabled(S2Feature.SKILL);
        when(repository.nameExists("复杂度标注", null)).thenReturn(true);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new SkillRequest("复杂度标注", null, "内容", true)));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
        verify(repository, never()).insert(anyString(), any(), anyString(), anyBoolean());
    }
}
