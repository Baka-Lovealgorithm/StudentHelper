package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.SessionDetailResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.infra.persistence.MessageFeedbackRepository;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.PracticeRepository;
import com.ai.studenthelper.infra.persistence.StepUnlockRepository;
import com.ai.studenthelper.infra.persistence.SessionRepository;
import com.ai.studenthelper.infra.storage.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionApplicationServiceTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageFeedbackRepository feedbackRepository;
    @Mock
    private ImageStorage imageStorage;
    @Mock
    private KnowledgePointRepository knowledgePointRepository;
    @Mock
    private PracticeRepository practiceRepository;
    @Mock
    private StepUnlockRepository stepUnlockRepository;

    private SessionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SessionApplicationService(sessionRepository, messageRepository,
                feedbackRepository, imageStorage, knowledgePointRepository,
                practiceRepository, stepUnlockRepository);
    }

    @Test
    void createUsesDefaultTitleAndFullAnswerMode() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setId(11L);
            return s;
        });

        var response = service.create("   ");

        assertEquals(11L, response.id());
        assertEquals("新会话", response.title());
        assertEquals(0, response.guideMode());
        assertNotNull(response.createdAt());
    }

    @Test
    void detailReturnsMessagesInRepositoryOrder() {
        Session session = new Session();
        session.setId(2L);
        session.setTitle("数据结构");
        session.setGuideMode(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        Message message = new Message();
        message.setId(3L);
        message.setRole("USER");
        message.setContent("题面");
        message.setStatus("SUCCESS");
        message.setCreatedAt(LocalDateTime.now());
        when(sessionRepository.findById(2L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionId(2L)).thenReturn(List.of(message));

        SessionDetailResponse detail = service.detail(2L);

        assertEquals("数据结构", detail.session().title());
        assertEquals(1, detail.messages().size());
        assertEquals("题面", detail.messages().get(0).content());
    }

    @Test
    void missingSessionThrowsE1003() {
        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(99L));
        assertEquals(ErrorCode.E1003, ex.getErrorCode());
    }

    @Test
    void deleteCascadesMessagesAndImageDirectory() {
        Session session = new Session();
        session.setId(4L);
        when(sessionRepository.findById(4L)).thenReturn(Optional.of(session));

        service.delete(4L);

        verify(feedbackRepository).deleteBySessionId(4L);
        verify(messageRepository).deleteBySessionId(4L);
        verify(sessionRepository).deleteById(4L);
        verify(imageStorage).deleteSessionDir(4L);
    }

    @Test
    void deleteMessageCascadesFeedbackMarks() {
        service.deleteMessage(7L);

        verify(feedbackRepository).deleteByMessageId(7L);
        verify(messageRepository).deleteById(7L);
    }

    @Test
    void guideModeIsPersisted() {
        Session session = new Session();
        session.setId(6L);
        session.setGuideMode(0);
        when(sessionRepository.findById(6L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(1, service.updateGuideMode(6L, 1).guideMode());
    }

    @Test
    void listPageReportsTotalAndHasMore() {
        Session session = new Session();
        session.setId(8L);
        session.setTitle("分页会话");
        when(sessionRepository.count()).thenReturn(23L);
        when(sessionRepository.findPage(10, 10)).thenReturn(List.of(session));

        var page = service.listPage(1, 10);

        assertEquals(23L, page.total());
        assertEquals(1, page.items().size());
        assertEquals("分页会话", page.items().get(0).title());
        assertTrue(page.hasMore(), "已取 20 条中的第 11 条，总数 23，仍有剩余");
    }

    @Test
    void listPageLastBatchHasNoMore() {
        Session a = new Session(); a.setId(1L); a.setTitle("A");
        Session b = new Session(); b.setId(2L); b.setTitle("B");
        Session c = new Session(); c.setId(3L); c.setTitle("C");
        when(sessionRepository.count()).thenReturn(3L);
        when(sessionRepository.findPage(10, 0)).thenReturn(List.of(a, b, c));

        var page = service.listPage(0, 10);

        assertEquals(3L, page.total());
        assertEquals(3, page.items().size());
        assertFalse(page.hasMore());
    }

    @Test
    void renameTrimsTitleAndKeepsUpdatedAtStable() {
        Session session = new Session();
        session.setId(9L);
        session.setTitle("旧标题");
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 11, 9, 10, 0);
        session.setUpdatedAt(originalUpdatedAt);
        when(sessionRepository.findById(9L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.rename(9L, "  新标题  ");

        assertEquals("新标题", response.title());
        assertEquals(originalUpdatedAt, response.updatedAt(), "重命名不应改变排序位置");
    }

    @Test
    void renameRejectsBlankTitleAndMissingSession() {
        Session session = new Session();
        session.setId(9L);
        session.setTitle("旧标题");
        when(sessionRepository.findById(9L)).thenReturn(Optional.of(session));
        BusinessException blank = assertThrows(BusinessException.class, () -> service.rename(9L, "   "));
        assertEquals(ErrorCode.E1004, blank.getErrorCode());

        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());
        BusinessException missing = assertThrows(BusinessException.class, () -> service.rename(99L, "标题"));
        assertEquals(ErrorCode.E1003, missing.getErrorCode());
    }
}
