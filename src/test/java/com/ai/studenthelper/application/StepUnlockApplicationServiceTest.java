package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.StepUnlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepUnlockApplicationServiceTest {

    @Mock private StepUnlockRepository repository;
    @Mock private MessageRepository messageRepository;
    @Mock private S2FeatureService features;

    private StepUnlockApplicationService service;

    @BeforeEach
    void setUp() {
        service = new StepUnlockApplicationService(repository, messageRepository, features);
    }

    @Test
    void disabledFeatureRejectsUnlock() {
        doThrow(new BusinessException(ErrorCode.E3004)).when(features).requireEnabled(S2Feature.STEP_UNLOCK);
        assertThrows(BusinessException.class, () -> service.unlock(1L, 2));
        verifyNoInteractions(repository);
    }

    @Test
    void unlockFillsAllPreviousSteps() {
        Message message = new Message();
        message.setId(7L);
        message.setSessionId(3L);
        when(messageRepository.findById(7L)).thenReturn(Optional.of(message));
        when(repository.maxUnlockedStep(7L)).thenReturn(3);
        assertEquals(3, service.unlock(7L, 3).unlockedStep());
        verify(repository).unlock(3L, 7L, 1);
        verify(repository).unlock(3L, 7L, 2);
        verify(repository).unlock(3L, 7L, 3);
    }

    @Test
    void closedFeatureReportsFullRender() {
        when(features.isEnabled(S2Feature.STEP_UNLOCK)).thenReturn(false);
        assertEquals(3, service.state(7L).unlockedStep());
        verifyNoInteractions(repository);
    }

    @Test
    void invalidStepIsRejected() {
        assertThrows(BusinessException.class, () -> service.unlock(7L, 4));
        verifyNoInteractions(repository);
    }
}
