package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.StepUnlockResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.StepUnlockRepository;
import org.springframework.stereotype.Service;

/**
 * T-209 分步解锁（DD-07）：后端仍生成完整六段，前端按 step1=①②③ / step2=④ /
 * step3=⑤⑥ 分段渲染；解锁只写记录、不重新调用模型。
 */
@Service
public class StepUnlockApplicationService {

    private final StepUnlockRepository repository;
    private final MessageRepository messageRepository;
    private final S2FeatureService features;

    public StepUnlockApplicationService(StepUnlockRepository repository,
                                        MessageRepository messageRepository,
                                        S2FeatureService features) {
        this.repository = repository;
        this.messageRepository = messageRepository;
        this.features = features;
    }

    public StepUnlockResponse unlock(Long messageId, int step) {
        features.requireEnabled(S2Feature.STEP_UNLOCK);
        if (messageId == null || step < 1 || step > 3) {
            throw new BusinessException(ErrorCode.E1004, "解锁级别必须是 1 / 2 / 3");
        }
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "消息不存在"));
        // 逐级解锁：解锁第 N 级时先补齐 1..N，保证刷新后状态连续
        for (int i = 1; i <= step; i++) {
            repository.unlock(message.getSessionId(), messageId, i);
        }
        return new StepUnlockResponse(messageId, repository.maxUnlockedStep(messageId));
    }

    public StepUnlockResponse state(Long messageId) {
        if (messageId == null) {
            throw new BusinessException(ErrorCode.E1004, "messageId 必填");
        }
        if (!features.isEnabled(S2Feature.STEP_UNLOCK)) {
            return new StepUnlockResponse(messageId, 3);
        }
        return new StepUnlockResponse(messageId, repository.maxUnlockedStep(messageId));
    }
}
