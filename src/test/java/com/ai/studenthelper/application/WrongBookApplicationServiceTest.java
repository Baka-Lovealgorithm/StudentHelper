package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.dto.WrongBookResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.MessageFeedback;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.MessageFeedbackRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.WrongBookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WrongBookApplicationServiceTest {

    @Mock private MessageFeedbackRepository feedbackRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private WrongBookRepository wrongBookRepository;
    @Mock private S2FeatureService features;

    private WrongBookApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WrongBookApplicationService(feedbackRepository, messageRepository,
                wrongBookRepository, features);
    }

    @Test
    void duplicateMarkReturnsExistingRow() {
        Message message = new Message();
        message.setId(9L);
        message.setSessionId(2L);
        when(messageRepository.findById(9L)).thenReturn(Optional.of(message));
        MessageFeedback existing = new MessageFeedback();
        existing.setId(5L);
        existing.setMessageId(9L);
        existing.setSessionId(2L);
        existing.setKind("WRONG");
        when(feedbackRepository.findByMessageIdAndKind(9L, "WRONG")).thenReturn(Optional.of(existing));
        assertEquals(5L, service.mark(9L, "数据结构", "再看一遍").id());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void disabledFeatureReturnsEmptyList() {
        when(features.isEnabled(S2Feature.WRONG_BOOK)).thenReturn(false);
        assertTrue(service.list("数据结构", null, null, 0, 20).isEmpty());
        verifyNoInteractions(wrongBookRepository);
    }

    @Test
    void invalidDateIsRejected() {
        when(features.isEnabled(S2Feature.WRONG_BOOK)).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> service.list(null, "2026/11/09", null, 0, 20));
    }

    @Test
    void listPassesFiltersAndPaging() {
        when(features.isEnabled(S2Feature.WRONG_BOOK)).thenReturn(true);
        when(wrongBookRepository.query(eq("数据结构"), any(), any(), eq(1), eq(10)))
                .thenReturn(List.<WrongBookResponse>of());
        assertTrue(service.list("数据结构", null, null, 1, 10).isEmpty());
        verify(wrongBookRepository).query(eq("数据结构"), any(), any(), eq(1), eq(10));
    }
}
