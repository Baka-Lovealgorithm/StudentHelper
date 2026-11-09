package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.LearningStatsResponse;
import com.ai.studenthelper.common.dto.LearningStatsResponse.DayCount;
import com.ai.studenthelper.common.dto.LearningStatsResponse.SubjectCount;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.LearningStatsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningStatsApplicationServiceTest {

    private final LearningStatsRepository repository = mock(LearningStatsRepository.class);
    private final SubjectApplicationService subjects = mock(SubjectApplicationService.class);
    private final S2FeatureService features = mock(S2FeatureService.class);
    private final LearningStatsApplicationService service =
            new LearningStatsApplicationService(repository, subjects, features);

    @Test
    void disabledStateRejectsWithE3004() {
        doThrow(new BusinessException(ErrorCode.E3004)).when(features)
                .requireEnabled(S2Feature.LEARNING_STATS);
        BusinessException error = assertThrows(BusinessException.class, () -> service.summary());
        assertEquals(ErrorCode.E3004, error.getErrorCode());
        verifyNoInteractions(repository);
    }

    @Test
    void summaryMirrorsRepositoryValues() {
        doNothing().when(features).requireEnabled(S2Feature.LEARNING_STATS);
        when(repository.sessions()).thenReturn(7);
        when(repository.questions()).thenReturn(21);
        when(repository.answers()).thenReturn(20);
        when(repository.failedAnswers()).thenReturn(1);
        when(repository.promptTokens()).thenReturn(1500L);
        when(repository.completionTokens()).thenReturn(2600L);
        when(repository.avgDurationMs()).thenReturn(9000);
        when(repository.byDay()).thenReturn(List.of(new DayCount("2026-11-09", 5)));
        when(repository.bySubject()).thenReturn(List.of(new SubjectCount("数据结构", 4)));
        when(subjects.currentForPrompt())
                .thenReturn(new SubjectApplicationService.SubjectContext("数据结构", null));

        LearningStatsResponse response = service.summary();
        assertEquals(7, response.sessions());
        assertEquals(21, response.questions());
        assertEquals(20, response.answers());
        assertEquals(1, response.failedAnswers());
        assertEquals(1500L, response.promptTokens());
        assertEquals(2600L, response.completionTokens());
        assertEquals(9000, response.avgDurationMs());
        assertEquals("数据结构", response.currentSubject());
        assertEquals(1, response.byDay().size());
        assertEquals(4, response.bySubject().get(0).count());
    }

    @Test
    void emptyDataReturnsZerosAndEmptyLists() {
        doNothing().when(features).requireEnabled(S2Feature.LEARNING_STATS);
        when(repository.byDay()).thenReturn(List.of());
        when(repository.bySubject()).thenReturn(List.of());
        when(subjects.currentForPrompt())
                .thenReturn(new SubjectApplicationService.SubjectContext("数据结构", null));
        LearningStatsResponse response = service.summary();
        assertEquals(0, response.sessions());
        assertTrue(response.byDay().isEmpty());
        assertTrue(response.bySubject().isEmpty());
    }
}
