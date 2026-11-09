package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.FeedbackResponse;
import com.ai.studenthelper.common.dto.WrongBookResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.MessageFeedback;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.MessageFeedbackRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.WrongBookRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/** T-202 错题本：复用 t_message_feedback 的 WRONG 标记，不另建表（AC-202-1）。 */
@Service
public class WrongBookApplicationService {

    private final MessageFeedbackRepository feedbackRepository;
    private final MessageRepository messageRepository;
    private final WrongBookRepository wrongBookRepository;
    private final S2FeatureService features;

    public WrongBookApplicationService(MessageFeedbackRepository feedbackRepository,
                                       MessageRepository messageRepository,
                                       WrongBookRepository wrongBookRepository,
                                       S2FeatureService features) {
        this.feedbackRepository = feedbackRepository;
        this.messageRepository = messageRepository;
        this.wrongBookRepository = wrongBookRepository;
        this.features = features;
    }

    public FeedbackResponse mark(Long messageId, String subject, String note) {
        features.requireEnabled(S2Feature.WRONG_BOOK);
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "消息不存在"));
        MessageFeedback existing = feedbackRepository
                .findByMessageIdAndKind(message.getId(), "WRONG").orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }
        MessageFeedback row = new MessageFeedback();
        row.setMessageId(message.getId());
        row.setSessionId(message.getSessionId());
        row.setKind("WRONG");
        row.setSubject(subject == null || subject.isBlank() ? "数据结构" : subject.trim());
        row.setNote(note == null || note.isBlank() ? null : note.trim());
        row.setCreatedAt(LocalDateTime.now());
        MessageFeedback saved = feedbackRepository.save(row);
        return toResponse(saved);
    }

    /** AC-202-2：学科 / 时间过滤 + 分页；关闭态返回空列表。 */
    public List<WrongBookResponse> list(String subject, String from, String to, int page, int size) {
        if (!features.isEnabled(S2Feature.WRONG_BOOK)) {
            return List.of();
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.E1004, "分页参数不合法");
        }
        return wrongBookRepository.query(subject, parseStart(from), parseEnd(to), page, size);
    }

    private static LocalDateTime parseStart(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.E1004, "起始日期格式应为 yyyy-MM-dd");
        }
    }

    private static LocalDateTime parseEnd(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atTime(LocalTime.MAX);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.E1004, "结束日期格式应为 yyyy-MM-dd");
        }
    }

    private static FeedbackResponse toResponse(MessageFeedback f) {
        return new FeedbackResponse(f.getId(), f.getMessageId(), f.getSessionId(),
                f.getKind(), f.getSubject(), f.getNote(), f.getCreatedAt());
    }
}
