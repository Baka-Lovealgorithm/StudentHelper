package com.ai.studenthelper.infra.log;

import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** D-01：E2005 时模型原始输出必须落库，且标记为 FAILED 不进入上下文。 */
class RawAnswerArchiverTest {

    private final MessageRepository repository = mock(MessageRepository.class);
    private final RawAnswerArchiver archiver = new RawAnswerArchiver(repository);

    @Test
    void archivesRawAnswerAsFailedAssistantMessage() {
        String raw = "## ① 题目理解\n只有一段，缺少后续段落";
        archiver.archive(7L, 1, raw, ErrorCode.E2005);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(repository).save(captor.capture());
        Message saved = captor.getValue();
        assertEquals(7L, saved.getSessionId());
        assertEquals("ASSISTANT", saved.getRole());
        assertEquals(raw, saved.getContent());
        assertEquals("FAILED", saved.getStatus());
        assertEquals("E2005", saved.getErrorCode());
        assertEquals(1, saved.getGuideMode());
        assertNotNull(saved.getCreatedAt());
        assertNull(saved.getHint(), "归档消息不携带用户提示词");
    }

    @Test
    void skipsBlankOrNullAnswer() {
        archiver.archive(7L, 0, "  ", ErrorCode.E2005);
        archiver.archive(7L, 0, null, ErrorCode.E2005);
        verifyNoInteractions(repository);
    }

    @Test
    void nullGuideModeFallsBackToDefault() {
        archiver.archive(7L, null, "raw", ErrorCode.E2005);
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(repository).save(captor.capture());
        assertEquals(0, captor.getValue().getGuideMode());
    }
}
