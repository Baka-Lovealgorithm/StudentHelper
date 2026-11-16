package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.SessionDetailResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.infra.persistence.MessageFeedbackRepository;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.ModelProviderRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
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
    private StepUnlockRepository stepUnlockRepository;
    @Mock
    private ModelProviderRepository modelProviderRepository;
    @Mock
    private com.ai.studenthelper.infra.memory.SessionSummaryStore sessionSummaryStore;

    private SessionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SessionApplicationService(sessionRepository, messageRepository,
                feedbackRepository, imageStorage, knowledgePointRepository,
                stepUnlockRepository, modelProviderRepository,
                sessionSummaryStore);
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
        // TCR-040 / AC-401-10：摘要缓存随会话清理，避免孤儿文件
        verify(sessionSummaryStore).delete(4L);
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

    @Test
    void createInheritsGlobalDefaultModel() {
        when(modelProviderRepository.findDefaultModelId()).thenReturn(42L);
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.create("新会话");

        assertEquals(42L, response.modelId(), "新会话应继承全局默认模型（CR-012）");
    }

    @Test
    void setModelPersistsOnlyCurrentSession() {
        Session session = new Session();
        session.setId(6L);
        session.setTitle("会话 6");
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 11, 9, 10, 0);
        session.setUpdatedAt(originalUpdatedAt);
        when(sessionRepository.findById(6L)).thenReturn(Optional.of(session));
        when(modelProviderRepository.findModel(42L)).thenReturn(Optional.of(
                new ModelProviderRepository.ModelRow(42L, 1L, "vision-model", true, true, null, false, false)));
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        var response = service.setModel(6L, 42L);

        assertEquals(42L, response.modelId());
        assertEquals(originalUpdatedAt, response.updatedAt(), "切模型不应打乱会话列表排序");
        verify(sessionRepository).save(session);
    }

    @Test
    void setModelRejectsUnknownModel() {
        Session session = new Session();
        session.setId(6L);
        when(sessionRepository.findById(6L)).thenReturn(Optional.of(session));
        when(modelProviderRepository.findModel(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.setModel(6L, 999L));
        assertEquals(ErrorCode.E3003, ex.getErrorCode());
        verify(sessionRepository, never()).save(any(Session.class));
    }

    // ===== CR-033：模型自动标题（AC-104-12~13） =====

    @Test
    void updateAutoTitleWritesOnlyDefaultTitleAndKeepsUpdatedAt() {
        Session session = new Session();
        session.setId(21L);
        session.setTitle("新会话");
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2026, 11, 12, 9, 0);
        session.setUpdatedAt(originalUpdatedAt);
        when(sessionRepository.findById(21L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        service.updateAutoTitle(21L, "  循环冗余校验 CRC  ");

        assertEquals("循环冗余校验 CRC", session.getTitle());
        assertEquals(originalUpdatedAt, session.getUpdatedAt(), "自动标题不应改变列表排序");
        verify(sessionRepository).save(session);
    }

    @Test
    void updateAutoTitleNeverOverwritesManualTitle() {
        Session session = new Session();
        session.setId(22L);
        session.setTitle("我的期末复习");
        when(sessionRepository.findById(22L)).thenReturn(Optional.of(session));

        service.updateAutoTitle(22L, "模型生成的标题");

        assertEquals("我的期末复习", session.getTitle(), "手动重命名过的标题永不被覆盖");
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    void updateAutoTitleIgnoresBlankTitle() {
        // 空白 / null 标题在触达仓库前直接返回，无需桩 findById
        service.updateAutoTitle(23L, "   ");
        service.updateAutoTitle(23L, null);

        verify(sessionRepository, never()).findById(anyLong());
        verify(sessionRepository, never()).save(any(Session.class));
    }
}
