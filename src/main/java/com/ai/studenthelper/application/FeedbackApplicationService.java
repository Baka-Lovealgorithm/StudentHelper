package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.FeedbackRequest;
import com.ai.studenthelper.common.dto.FeedbackResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.MessageFeedback;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.MessageFeedbackRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 反馈闭环用例编排（T-207 / FR-207）。
 * 标记写入 t_message_feedback，与 T-202 错题本共用一张表；重复标记返回既有记录，
 * 依赖唯一键 uk_feedback(message_id, kind) 保证不产生重复行（AC-202-4 / AC-207-1）。
 */
@Service
public class FeedbackApplicationService {

    private static final Set<String> KINDS = Set.of("WRONG", "INCORRECT", "REGENERATE");

    private final MessageFeedbackRepository feedbackRepository;
    private final MessageRepository messageRepository;
    private final S2FeatureService features;

    public FeedbackApplicationService(MessageFeedbackRepository feedbackRepository,
                                      MessageRepository messageRepository,
                                      S2FeatureService features) {
        this.feedbackRepository = feedbackRepository;
        this.messageRepository = messageRepository;
        this.features = features;
    }

    @Transactional
    public FeedbackResponse mark(FeedbackRequest request) {
        features.requireEnabled(S2Feature.FEEDBACK);
        if (request == null || request.messageId() == null || request.kind() == null
                || !KINDS.contains(request.kind().toUpperCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.E1004,
                    "反馈类型必须是 WRONG / INCORRECT / REGENERATE，且 messageId 必填");
        }
        String kind = request.kind().toUpperCase(Locale.ROOT);
        Message message = messageRepository.findById(request.messageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "消息不存在"));

        MessageFeedback existing = feedbackRepository
                .findByMessageIdAndKind(message.getId(), kind).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        MessageFeedback feedback = new MessageFeedback();
        feedback.setMessageId(message.getId());
        feedback.setSessionId(message.getSessionId());
        feedback.setKind(kind);
        feedback.setSubject(trimToNull(request.subject()));
        feedback.setNote(trimToNull(request.note()));
        feedback.setCreatedAt(LocalDateTime.now());
        return toResponse(feedbackRepository.save(feedback));
    }

    public List<FeedbackResponse> list(Long sessionId, String kind) {
        if (!features.isEnabled(S2Feature.FEEDBACK)) {
            return List.of();
        }
        List<MessageFeedback> rows;
        if (sessionId != null) {
            rows = feedbackRepository.findBySessionId(sessionId);
        } else if (kind != null && !kind.isBlank()) {
            rows = feedbackRepository.findByKind(kind.toUpperCase(Locale.ROOT));
        } else {
            rows = feedbackRepository.findAllOrdered();
        }
        return rows == null ? List.of() : rows.stream().map(this::toResponse).toList();
    }

    private FeedbackResponse toResponse(MessageFeedback f) {
        return new FeedbackResponse(f.getId(), f.getMessageId(), f.getSessionId(),
                f.getKind(), f.getSubject(), f.getNote(), f.getCreatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().length() > 500 ? value.trim().substring(0, 500) : value.trim();
    }
}
