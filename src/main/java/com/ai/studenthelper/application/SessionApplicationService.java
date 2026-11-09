package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.MessageResponse;
import com.ai.studenthelper.common.dto.SessionDetailResponse;
import com.ai.studenthelper.common.dto.SessionPageResponse;
import com.ai.studenthelper.common.dto.SessionResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.infra.persistence.MessageFeedbackRepository;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.ModelProviderRepository;
import com.ai.studenthelper.infra.persistence.PracticeRepository;
import com.ai.studenthelper.infra.persistence.StepUnlockRepository;
import com.ai.studenthelper.infra.persistence.SessionRepository;
import com.ai.studenthelper.infra.storage.ImageStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 会话用例编排：会话与历史的读写、级联清理、事务边界（FR-104）。 */
@Service
public class SessionApplicationService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final MessageFeedbackRepository feedbackRepository;
    private final ImageStorage imageStorage;
    private final KnowledgePointRepository knowledgePointRepository;
    private final PracticeRepository practiceRepository;
    private final StepUnlockRepository stepUnlockRepository;
    private final ModelProviderRepository modelProviderRepository;

    public SessionApplicationService(SessionRepository sessionRepository,
                                     MessageRepository messageRepository,
                                     MessageFeedbackRepository feedbackRepository,
                                     ImageStorage imageStorage,
                                     KnowledgePointRepository knowledgePointRepository,
                                     PracticeRepository practiceRepository,
                                     StepUnlockRepository stepUnlockRepository,
                                     ModelProviderRepository modelProviderRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.feedbackRepository = feedbackRepository;
        this.imageStorage = imageStorage;
        this.knowledgePointRepository = knowledgePointRepository;
        this.practiceRepository = practiceRepository;
        this.stepUnlockRepository = stepUnlockRepository;
        this.modelProviderRepository = modelProviderRepository;
    }

    public SessionResponse create(String title) {
        LocalDateTime now = LocalDateTime.now();
        Session session = new Session();
        session.setTitle(title == null || title.isBlank() ? "新会话" : title.trim());
        session.setGuideMode(0);
        // CR-012：新会话继承全局默认模型（迁移后为原 is_current 对应条目）
        session.setModelId(modelProviderRepository.findDefaultModelId());
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return toResponse(sessionRepository.save(session));
    }

    public List<SessionResponse> list() {
        return sessionRepository.findAllOrdered().stream().map(this::toResponse).toList();
    }

    /**
     * 会话分页：按 updated_at / id 倒序，offset 由 page * size 计算。
     * 前端只有「加载更多」，不暴露页码跳转；page 从 0 开始。
     */
    public SessionPageResponse listPage(int page, int size) {
        long total = sessionRepository.count();
        long offset = (long) page * size;
        List<SessionResponse> items = sessionRepository.findPage(size, offset).stream()
                .map(this::toResponse).toList();
        return new SessionPageResponse(items, total, offset + items.size() < total);
    }

    /**
     * 重命名会话。刻意不更新 updated_at：重命名不是对话活动，避免会话在列表里
     * 跳到首位、打断用户正在浏览的位置。
     */
    public SessionResponse rename(Long id, String title) {
        Session session = requireSession(id);
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.E1004);
        }
        session.setTitle(normalized);
        return toResponse(sessionRepository.save(session));
    }

    /** CR-012：会话级模型选择，只改当前会话、不动 updated_at（不打断列表排序）。 */
    public SessionResponse setModel(Long id, Long modelId) {
        Session session = requireSession(id);
        if (modelId == null || modelProviderRepository.findModel(modelId).isEmpty()) {
            throw new BusinessException(ErrorCode.E3003, "模型不存在或已被删除");
        }
        session.setModelId(modelId);
        return toResponse(sessionRepository.save(session));
    }

    public SessionDetailResponse detail(Long id) {
        Session session = requireSession(id);
        List<MessageResponse> messages = messageRepository.findBySessionId(id).stream()
                .map(this::toResponse).toList();
        return new SessionDetailResponse(toResponse(session), messages);
    }

    @Transactional
    public void delete(Long id) {
        requireSession(id);
        // S2：反馈标记随会话级联清理，避免孤儿行（AC-202-3 / AC-207-4）
        feedbackRepository.deleteBySessionId(id);
        knowledgePointRepository.removeSessionHits(id);
        // Gate-2 复跑发现：t_practice / t_step_unlock 曾残留，现一并清理（AC-202-3 / AC-209-x）
        practiceRepository.deleteBySessionId(id);
        stepUnlockRepository.deleteBySessionId(id);
        messageRepository.deleteBySessionId(id);
        sessionRepository.deleteById(id);
        imageStorage.deleteSessionDir(id);
    }

    @Transactional
    public void deleteMessage(Long id) {
        feedbackRepository.deleteByMessageId(id);
        knowledgePointRepository.removeMessageHits(id);
        practiceRepository.deleteBySourceMessageId(id);
        stepUnlockRepository.deleteByMessageId(id);
        messageRepository.deleteById(id);
    }

    public SessionResponse updateGuideMode(Long id, int guideMode) {
        Session session = requireSession(id);
        session.setGuideMode(guideMode);
        session.setUpdatedAt(LocalDateTime.now());
        return toResponse(sessionRepository.save(session));
    }

    /** 供解题主链路（T-101 起）复用。 */
    public Session requireSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1003));
    }

    public Message saveMessage(Message message) {
        return messageRepository.save(message);
    }

    private SessionResponse toResponse(Session s) {
        return new SessionResponse(s.getId(), s.getTitle(), s.getGuideMode(), s.getModelId(),
                s.getCreatedAt(), s.getUpdatedAt());
    }

    private MessageResponse toResponse(Message m) {
        return new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getHint(), m.getImagePaths(),
                m.getStatus(), m.getErrorCode(), m.getCreatedAt());
    }
}
