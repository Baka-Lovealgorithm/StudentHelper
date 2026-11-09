package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.FeedbackRequest;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.MessageFeedback;
import com.ai.studenthelper.infra.persistence.MessageFeedbackRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T-207 反馈闭环：幂等标记、参数校验与查询分支。 */
@ExtendWith(MockitoExtension.class)
class FeedbackApplicationServiceTest {

    @Mock
    private MessageFeedbackRepository feedbackRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private S2FeatureService features;

    private FeedbackApplicationService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackApplicationService(feedbackRepository, messageRepository, features);
        // mark 用例不调用 isEnabled，用 lenient 避免 Mockito 严格桩报错
        lenient().when(features.isEnabled(any())).thenReturn(true);
    }

    private Message existingMessage() {
        Message message = new Message();
        message.setId(10L);
        message.setSessionId(2L);
        message.setRole("ASSISTANT");
        message.setStatus("SUCCESS");
        return message;
    }

    @Test
    void markCreatesFeedbackUsingMessageSession() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(existingMessage()));
        when(feedbackRepository.findByMessageIdAndKind(10L, "INCORRECT")).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(MessageFeedback.class))).thenAnswer(invocation -> {
            MessageFeedback f = invocation.getArgument(0);
            f.setId(99L);
            return f;
        });

        var response = service.mark(new FeedbackRequest(10L, "incorrect", "数据结构", "答案有误"));

        assertEquals(99L, response.id());
        assertEquals(10L, response.messageId());
        assertEquals(2L, response.sessionId());
        assertEquals("INCORRECT", response.kind());
        assertEquals("数据结构", response.subject());
    }

    @Test
    void duplicateMarkReturnsExistingRowWithoutInsert() {
        MessageFeedback existing = new MessageFeedback();
        existing.setId(77L);
        existing.setMessageId(10L);
        existing.setSessionId(2L);
        existing.setKind("INCORRECT");
        when(messageRepository.findById(10L)).thenReturn(Optional.of(existingMessage()));
        when(feedbackRepository.findByMessageIdAndKind(10L, "INCORRECT"))
                .thenReturn(Optional.of(existing));

        var response = service.mark(new FeedbackRequest(10L, "INCORRECT", null, null));

        assertEquals(77L, response.id());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void invalidKindThrowsE1004() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.mark(new FeedbackRequest(10L, "UNKNOWN", null, null)));
        assertEquals(ErrorCode.E1004, ex.getErrorCode());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void missingMessageThrowsE1004() {
        when(messageRepository.findById(10L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.mark(new FeedbackRequest(10L, "WRONG", null, null)));
        assertEquals(ErrorCode.E1004, ex.getErrorCode());
    }

    @Test
    void blankOptionalFieldsAreStoredAsNull() {
        when(messageRepository.findById(10L)).thenReturn(Optional.of(existingMessage()));
        when(feedbackRepository.findByMessageIdAndKind(10L, "REGENERATE")).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(MessageFeedback.class))).thenAnswer(invocation -> {
            MessageFeedback f = invocation.getArgument(0);
            f.setId(5L);
            return f;
        });

        var response = service.mark(new FeedbackRequest(10L, "REGENERATE", "  ", "   "));

        assertNull(response.subject());
        assertNull(response.note());
    }

    @Test
    void listPrefersSessionFilterOverKind() {
        when(feedbackRepository.findBySessionId(2L)).thenReturn(List.of());

        assertEquals(0, service.list(2L, "WRONG").size());

        verify(feedbackRepository).findBySessionId(2L);
        verify(feedbackRepository, never()).findByKind(any());
    }

    @Test
    void listFallsBackToKindFilter() {
        when(feedbackRepository.findByKind("WRONG")).thenReturn(List.of());
        assertEquals(0, service.list(null, "wrong").size());
        verify(feedbackRepository).findByKind("WRONG");
    }
}
