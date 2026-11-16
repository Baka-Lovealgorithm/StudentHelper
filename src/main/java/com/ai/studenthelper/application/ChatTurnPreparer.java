package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.ChatContextAssembler;
import com.ai.studenthelper.agent.IntentClassifier;
import com.ai.studenthelper.agent.S2PromptAugmenter;
import com.ai.studenthelper.agent.SolverPromptTemplate;
import com.ai.studenthelper.common.dto.ChatCommand;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.domain.enums.AnswerIntent;
import com.ai.studenthelper.domain.enums.GuideMode;
import com.ai.studenthelper.domain.enums.MessageRole;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import io.agentscope.core.message.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成前的装配（CR-043 / 候选 02）。
 *
 * <p>从 {@code ChatApplicationService.solve()} 切出来的第一段：判定形态 → 取学科运行时 →
 * 固化技能 → 装配 system prompt → 构造 {@link AnswerPlan} → 压缩 → 装配上下文。
 * 全程不落库、不发 SSE，输入是「会话 + 本轮用户消息」，输出是 {@link PreparedTurn}，
 * 因此可以脱离生成链路单测。</p>
 */
@Service
public class ChatTurnPreparer {

    private static final Logger log = LoggerFactory.getLogger(ChatTurnPreparer.class);

    private final MessageRepository messageRepository;
    private final IntentClassifier intentClassifier;
    private final SubjectSkillApplicationService subjectSkills;
    private final SkillApplicationService skillService;
    private final S2PromptAugmenter promptAugmenter;
    private final ChatContextAssembler contextAssembler;
    private final MemoryCompactionService compactionService;
    private final S2FeatureService features;

    public ChatTurnPreparer(MessageRepository messageRepository,
                            IntentClassifier intentClassifier,
                            SubjectSkillApplicationService subjectSkills,
                            SkillApplicationService skillService,
                            S2PromptAugmenter promptAugmenter,
                            ChatContextAssembler contextAssembler,
                            MemoryCompactionService compactionService,
                            S2FeatureService features) {
        this.messageRepository = messageRepository;
        this.intentClassifier = intentClassifier;
        this.subjectSkills = subjectSkills;
        this.skillService = skillService;
        this.promptAugmenter = promptAugmenter;
        this.contextAssembler = contextAssembler;
        this.compactionService = compactionService;
        this.features = features;
    }

    /**
     * @param session     已校验存在的会话（调用方只查一次库）
     * @param userMessage 刚落库的本轮用户消息（用于排除本轮历史）
     * @param config      已解析的模型配置
     */
    public PreparedTurn prepare(ChatCommand command, Session session, Message userMessage,
                                ModelConfigResolver.ResolvedModelConfig config) {
        GuideMode mode = GuideMode.fromCode(command.guideMode());
        List<Message> history = messageRepository.findBySessionId(command.sessionId());
        // 判别器要求「不含本轮消息」，否则本轮问题在输入里出现两次、首轮也打不出「无历史」。
        List<Message> priorHistory = excludeCurrentTurn(history, userMessage.getId());
        // CR-066：举一反三出题轮跳过意图分类——提示词含「出一道相似题」时分类器会按兜底规则判 SOLVE，
        // 进而套六段结构把答案也解出来；这里显式固定 FOLLOW_UP（自由问答），仍由 AnswerPlan 统一决策。
        AnswerIntent intent = command.practice()
                ? AnswerIntent.FOLLOW_UP
                : intentClassifier.classify(command.sessionId(), priorHistory,
                        command.text(), command.images().size(), config).intent();
        SubjectSkillRuntime runtime = subjectSkills.resolveRuntime(session);

        // CR-038：会话固化的通用技能每轮（含追问、压缩后）注入；未固化时为空
        List<String[]> fixedSkills = skillService.contentsForIds(
                SessionApplicationService.parseSkillIds(session.getSkillIds()));
        if (!fixedSkills.isEmpty()) {
            log.info("SESSION_SKILL_INJECT sessionId={} count={} names={} intent={}",
                    command.sessionId(), fixedSkills.size(),
                    fixedSkills.stream().map(row -> row[0]).toList(), intent);
        }
        // CR-043：本轮所有开关判断共用一份快照，一次请求只读一次开关表
        FeatureSnapshot snapshot = features.snapshot();
        String systemPrompt = promptAugmenter.buildAndAugment(intent, mode, runtime, fixedSkills,
                snapshot);
        // CR-066：出题约束追加在 system 末尾（形态段与增量之后），不改变三态公共头与既有追加顺序
        if (command.practice()) {
            systemPrompt = systemPrompt + SolverPromptTemplate.practiceBlock();
        }

        // CR-033：首轮（尚无助手消息）且标题仍为默认「新会话」时追加标题指令；追问轮不追加
        boolean firstRound = history.stream()
                .noneMatch(m -> MessageRole.ASSISTANT.name().equals(m.getRole()));
        boolean autoTitle = firstRound && intent != AnswerIntent.FOLLOW_UP
                && SessionApplicationService.DEFAULT_TITLE.equals(session.getTitle());
        if (autoTitle) {
            systemPrompt = systemPrompt + SolverPromptTemplate.sessionTitleBlock();
        }

        AnswerPlan plan = AnswerPlan.build(intent, mode, systemPrompt,
                runtime.needsTools(), !skillService.catalog().isEmpty(), autoTitle, runtime, snapshot);

        // T-401：压缩只作用于「本轮之前」的历史
        MemoryCompactionService.CompactionOutcome compaction =
                compactionService.compactIfNeeded(command.sessionId(), priorHistory, config);
        List<Msg> messages = switch (plan.intent()) {
            case FOLLOW_UP -> contextAssembler.assembleFollowUp(compaction.history(),
                    compaction.summary(), compaction.fullWindow(), command.parts(),
                    command.text(), command.hint(), command.images());
            case EXPLAIN -> contextAssembler.assembleExplain(compaction.history(),
                    compaction.summary(), compaction.fullWindow(), command.parts(),
                    command.text(), command.hint(), command.images());
            case SOLVE -> contextAssembler.assemble(compaction.history(),
                    compaction.summary(), compaction.fullWindow(), command.parts(),
                    command.text(), command.hint(), command.images());
        };
        return new PreparedTurn(config, plan, messages, runtime);
    }

    /**
     * 取本轮之前的历史（判别器与压缩的输入）。
     *
     * <p>本轮用户消息在判定形态之前就已落库，{@code findBySessionId} 的结果必然含它；
     * 判别器 javadoc 要求「不含本轮」，否则输入里本轮问题出现两次。</p>
     */
    static List<Message> excludeCurrentTurn(List<Message> history, Long currentUserId) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        if (currentUserId == null) {
            return history;
        }
        Message last = history.get(history.size() - 1);
        return currentUserId.equals(last.getId())
                ? new ArrayList<>(history.subList(0, history.size() - 1)) : history;
    }
}
