package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.MessageResponse;
import com.ai.studenthelper.common.dto.SessionDetailResponse;
import com.ai.studenthelper.common.dto.SessionResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.infra.persistence.MessageFeedbackRepository;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
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

    public SessionApplicationService(SessionRepository sessionRepository,
                                     MessageRepository messageRepository,
                                     MessageFeedbackRepository feedbackRepository,
                                     ImageStorage imageStorage,
                                     KnowledgePointRepository knowledgePointRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.feedbackRepository = feedbackRepository;
        this.imageStorage = imageStorage;
        this.knowledgePointRepository = knowledgePointRepository;
    }

    public SessionResponse create(String title) {
        LocalDateTime now = LocalDateTime.now();
        Session session = new Session();
        session.setTitle(title == null || title.isBlank() ? "新会话" : title.trim());
        session.setGuideMode(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return toResponse(sessionRepository.save(session));
    }

    public List<SessionResponse> list() {
        return sessionRepository.findAllOrdered().stream().map(this::toResponse).toList();
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
        messageRepository.deleteBySessionId(id);
        sessionRepository.deleteById(id);
        imageStorage.deleteSessionDir(id);
    }

    @Transactional
    public void deleteMessage(Long id) {
        feedbackRepository.deleteByMessageId(id);
        knowledgePointRepository.removeMessageHits(id);
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
        return new SessionResponse(s.getId(), s.getTitle(), s.getGuideMode(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private MessageResponse toResponse(Message m) {
        return new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getHint(), m.getImagePaths(),
                m.getStatus(), m.getErrorCode(), m.getCreatedAt());
    }
}
