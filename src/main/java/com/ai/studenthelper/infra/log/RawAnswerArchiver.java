package com.ai.studenthelper.infra.log;

import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.MessageRole;
import com.ai.studenthelper.domain.enums.MessageStatus;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * E2005（生成结果格式异常）时把模型原始输出归档落库，供调试（AC-102、缺陷 D-01）。
 * 归档消息标记为 FAILED，ChatContextAssembler 会过滤，不进入后续上下文。
 */
@Component
public class RawAnswerArchiver {

    private final MessageRepository messageRepository;

    public RawAnswerArchiver(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public void archive(Long sessionId, Integer guideMode, String rawAnswer, ErrorCode code) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            return;
        }
        Message message = new Message();
        message.setSessionId(sessionId);
        message.setRole(MessageRole.ASSISTANT.name());
        message.setContent(rawAnswer);
        message.setGuideMode(guideMode == null ? 0 : guideMode);
        message.setStatus(MessageStatus.FAILED.name());
        message.setErrorCode(code.getCode());
        message.setCreatedAt(LocalDateTime.now());
        messageRepository.save(message);
    }
}
