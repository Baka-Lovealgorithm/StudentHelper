package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.SolverPromptTemplate;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.LearningStatsResponse;
import com.ai.studenthelper.common.dto.LearningStatsResponse.DayStat;
import com.ai.studenthelper.common.dto.LearningStatsResponse.SubjectCount;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.LearningStatsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningStatsApplicationServiceTest {

    private final LearningStatsRepository repository = mock(LearningStatsRepository.class);
    private final S2FeatureService features = mock(S2FeatureService.class);
    private final LearningStatsApplicationService service =
            new LearningStatsApplicationService(repository, features);

    @Test
    void disabledStateRejectsWithE3004() {
        doThrow(new BusinessException(ErrorCode.E3004)).when(features)
                .requireEnabled(S2Feature.LEARNING_STATS);
        BusinessException error = assertThrows(BusinessException.class, () -> service.summary(7));
        assertEquals(ErrorCode.E3004, error.getErrorCode());
        verifyNoInteractions(repository);
    }

    @Test
    void summaryMirrorsRepositoryValuesWithoutTokenOrDuration() {
        doNothing().when(features).requireEnabled(S2Feature.LEARNING_STATS);
        when(repository.sessions(7)).thenReturn(7);
        when(repository.questions(7)).thenReturn(21);
        when(repository.answers(7)).thenReturn(20);
        when(repository.failedAnswers(7)).thenReturn(1);
        when(repository.byDay(7)).thenReturn(List.of(new DayStat("2026-11-11", 5, 4, 1)));
        when(repository.bySubject(7)).thenReturn(List.of(new SubjectCount("大学课程", 4)));

        LearningStatsResponse response = service.summary(7);
        assertEquals(7, response.days());
        assertEquals(7, response.sessions());
        assertEquals(21, response.questions());
        assertEquals(20, response.answers());
        assertEquals(1, response.failedAnswers());
        assertEquals(SolverPromptTemplate.GENERIC_SUBJECT, response.currentSubject());
        assertEquals(1, response.byDay().size());
        assertEquals(5, response.byDay().get(0).questions());
        assertEquals(4, response.bySubject().get(0).count());
    }

    @Test
    void missingDaysFallsBackToSeven() {
        doNothing().when(features).requireEnabled(S2Feature.LEARNING_STATS);
        LearningStatsResponse response = service.summary(null);
        assertEquals(7, response.days());
        verify(repository).questions(7);
    }

    @Test
    void zeroMeansAllTime() {
        doNothing().when(features).requireEnabled(S2Feature.LEARNING_STATS);
        LearningStatsResponse response = service.summary(0);
        assertEquals(0, response.days());
        verify(repository).questions(0);
    }

    @Test
    void unsupportedWindowRejectsWithE1004() {
        doNothing().when(features).requireEnabled(S2Feature.LEARNING_STATS);
        BusinessException error = assertThrows(BusinessException.class, () -> service.summary(2));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
        verifyNoInteractions(repository);
    }

    @Test
    void emptyDataReturnsZerosAndEmptyLists() {
        doNothing().when(features).requireEnabled(S2Feature.LEARNING_STATS);
        when(repository.byDay(7)).thenReturn(List.of());
        when(repository.bySubject(7)).thenReturn(null);
        LearningStatsResponse response = service.summary(7);
        assertEquals(0, response.sessions());
        assertTrue(response.byDay().isEmpty());
        assertTrue(response.bySubject().isEmpty());
    }
}
