package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.AgentPromptTemplates;
import com.ai.studenthelper.agent.SolverAgentFactory;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.PracticeResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.PracticeRepository;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * T-205 举一反三四步链路：生成相似题（不给答案）→ 作答 → 判分 → 记录。
 * 判分结果通过 kp_id 回填 mastery（DD-03）。
 */
@Service
public class PracticeApplicationService {

    private final PracticeRepository repository;
    private final MessageRepository messageRepository;
    private final KnowledgePointRepository knowledgeRepository;
    private final S2FeatureService features;
    private final ModelConfigResolver configResolver;
    private final SolverAgentFactory agentFactory;

    public PracticeApplicationService(PracticeRepository repository,
                                      MessageRepository messageRepository,
                                      KnowledgePointRepository knowledgeRepository,
                                      S2FeatureService features,
                                      ModelConfigResolver configResolver,
                                      SolverAgentFactory agentFactory) {
        this.repository = repository;
        this.messageRepository = messageRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.features = features;
        this.configResolver = configResolver;
        this.agentFactory = agentFactory;
    }

    public PracticeResponse generate(Long sourceMessageId, Long kpId) {
        features.requireEnabled(S2Feature.PRACTICE);
        if (sourceMessageId == null) {
            throw new BusinessException(ErrorCode.E1004, "sourceMessageId 必填");
        }
        Message source = messageRepository.findById(sourceMessageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E1004, "原解答消息不存在"));
        String variant = call(AgentPromptTemplates.PRACTICE_GENERATE,
                "原题参考解答：\n" + truncate(source.getContent(), 2500));
        if (variant.isBlank() || variant.contains("最终答案") || variant.contains("答案：")) {
            throw new BusinessException(ErrorCode.E2005, "生成的相似题不符合要求，请重试");
        }
        Long id = repository.create(sourceMessageId, source.getSessionId(), kpId, truncate(variant, 4000));
        return repository.findById(id);
    }

    public PracticeResponse answer(Long id, String userAnswer) {
        features.requireEnabled(S2Feature.PRACTICE);
        if (userAnswer == null || userAnswer.isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "请先作答");
        }
        requirePending(id);
        repository.saveAnswer(id, userAnswer.trim());
        return repository.findById(id);
    }

    /** 判分失败时不落半截成功状态（AC-205-4）。 */
    public PracticeResponse judge(Long id) {
        features.requireEnabled(S2Feature.PRACTICE);
        PracticeResponse practice = requirePending(id);
        if (practice.userAnswer() == null || practice.userAnswer().isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "请先作答再判分");
        }
        String raw = call(AgentPromptTemplates.PRACTICE_JUDGE,
                "相似题：\n" + practice.variantText()
                        + "\n\n用户作答：\n" + truncate(practice.userAnswer(), 2000));
        String[] lines = raw.trim().split("\\R");
        if (lines.length < 2 || !List.of("PASS", "FAIL").contains(lines[0].trim().toUpperCase())) {
            throw new BusinessException(ErrorCode.E2005, "判分结果格式异常，请重试");
        }
        String result = lines[0].trim().toUpperCase();
        int score;
        try {
            score = Math.max(0, Math.min(100, Integer.parseInt(lines[1].trim().replaceAll("[^0-9-]", ""))));
        } catch (NumberFormatException e) {
            score = "PASS".equals(result) ? 80 : 40;
        }
        String feedback = lines.length > 2 ? truncate(String.join(" ", List.of(lines).subList(2, lines.length)), 500) : "";
        repository.saveJudgement(id, result, BigDecimal.valueOf(score), feedback);
        knowledgeRepository.refreshMastery(repository.findKpId(id));
        return repository.findById(id);
    }

    public List<PracticeResponse> list(Long sessionId) {
        if (!features.isEnabled(S2Feature.PRACTICE)) {
            return List.of();
        }
        return sessionId == null ? List.of() : repository.findBySessionId(sessionId);
    }

    private PracticeResponse requirePending(Long id) {
        PracticeResponse practice = repository.findById(id);
        if (practice == null) {
            throw new BusinessException(ErrorCode.E1004, "练习记录不存在");
        }
        if (!"PENDING".equals(practice.judgeResult())) {
            throw new BusinessException(ErrorCode.E1004, "该练习已判分");
        }
        return practice;
    }

    private String call(String systemPrompt, String userText) {
        try {
            var config = configResolver.resolveCurrent();
            var agent = agentFactory.createNonStreaming(systemPrompt, config);
            Msg prompt = Msg.builder().role(MsgRole.USER).textContent(userText).build();
            Msg result = agent.call(List.of(prompt)).block(Duration.ofSeconds(20));
            return result == null || result.getTextContent() == null ? "" : result.getTextContent().trim();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.E2002, "辅助模型调用失败：" + safe(e));
        }
    }

    private static String safe(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
