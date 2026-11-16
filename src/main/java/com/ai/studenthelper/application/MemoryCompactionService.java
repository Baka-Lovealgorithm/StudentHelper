package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.AgentPromptTemplates;
import com.ai.studenthelper.agent.MemoryTools;
import com.ai.studenthelper.agent.SolverAgentFactory;
import com.ai.studenthelper.common.ImageContextPolicy;
import com.ai.studenthelper.common.TokenEstimator;
import com.ai.studenthelper.config.MemoryProperties;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.memory.SessionSummaryStore;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * T-401 / AC-401-4 / AC-401-8 / AC-401-9：对话压缩。
 *
 * <p>历史条数或估算 token 任一超阈值时，先把待压缩片段交给模型用 {@code memory_save} 写入 L1
 * （只写当日文件），再生成一段摘要替换前缀、保留最近 N 条。摘要按会话持久化，避免每次请求重复压缩；
 * 任一模型调用失败都原样返回历史，绝不阻断解题。</p>
 *
 * <p>TCR-040：开关由 {@code memory} 改为独立的 {@code conversationCompaction}；
 * 未触发压缩时 {@code fullWindow=true}，由装配层全部带入上下文，不再套 12 条硬截断——
 * 这样「既不压缩也不带入」的原 13~30 条空档被彻底消除。</p>
 */
@Service
public class MemoryCompactionService {

    private static final Logger log = LoggerFactory.getLogger(MemoryCompactionService.class);
    private static final int MAX_RENDER_CHARS = 8000;

    private final MemoryProperties properties;
    private final S2FeatureService features;
    private final SessionSummaryStore summaryStore;
    private final MemoryTools memoryTools;
    private final SolverAgentFactory agentFactory;
    /** CR-037：与装配器共用同一窗口策略，估算图片 token。 */
    private final ImageContextPolicy imagePolicy;

    @Autowired
    public MemoryCompactionService(MemoryProperties properties, S2FeatureService features,
                                   SessionSummaryStore summaryStore, MemoryTools memoryTools,
                                   SolverAgentFactory agentFactory, ImageContextPolicy imagePolicy) {
        this.properties = properties;
        this.features = features;
        this.summaryStore = summaryStore;
        this.memoryTools = memoryTools;
        this.agentFactory = agentFactory;
        this.imagePolicy = imagePolicy;
    }

    /** 兼容单测直连的 5 参构造：无图片策略，估算口径与 CR-036 后逐字一致。 */
    public MemoryCompactionService(MemoryProperties properties, S2FeatureService features,
                                   SessionSummaryStore summaryStore, MemoryTools memoryTools,
                                   SolverAgentFactory agentFactory) {
        this(properties, features, summaryStore, memoryTools, agentFactory, null);
    }

    /**
     * @param sessionId 当前会话
     * @param history   全部历史（不含本轮用户消息）
     * @param config    当前会话模型配置
     */
    public CompactionOutcome compactIfNeeded(Long sessionId, List<Message> history,
                                             ModelConfigResolver.ResolvedModelConfig config) {
        if (!features.isEnabled(S2Feature.CONVERSATION_COMPACTION) || history == null || history.isEmpty()) {
            // 开关关闭：fullWindow=false，装配层回退 S1 的 12 条硬截断（AC-204-1 关闭态口径）
            return new CompactionOutcome(history == null ? List.of() : history, null, false);
        }
        SessionSummaryStore.SessionSummary prior = summaryStore.read(sessionId).orElse(null);
        List<Message> effective = prior == null ? history : after(history, prior.uptoMessageId());
        String previous = prior == null ? null : prior.summary();

        int estimatedTokens = estimateTokens(effective, previous);
        if (!needsCompaction(effective.size(), estimatedTokens)) {
            // 未超阈值：整段带入（fullWindow=true），消除原 12~30 条之间的丢弃空档
            return new CompactionOutcome(effective, previous, true);
        }
        log.info("COMPACTION_TRIGGER sessionId={} messages={} estimatedTokens={} triggerMessages={} triggerTokens={}",
                sessionId, effective.size(), estimatedTokens,
                properties.getCompactionTriggerMessages(), properties.getCompactionTriggerTokens());

        int cut = Math.max(1, effective.size() - properties.getCompactionKeepMessages());
        List<Message> prefix = new ArrayList<>(effective.subList(0, cut));
        List<Message> tail = new ArrayList<>(effective.subList(cut, effective.size()));

        flushToDaily(prefix, config);
        String summary = summarize(previous, prefix, config);
        if (summary == null || summary.isBlank()) {
            // 压缩失败：整段带入并保留旧摘要，宁可多花 token 也不丢历史
            return new CompactionOutcome(effective, previous, true);
        }

        Long upto = prefix.get(prefix.size() - 1).getId();
        try {
            summaryStore.write(sessionId, summary, upto);
        } catch (Exception e) {
            log.warn("COMPACTION_SUMMARY_SAVE_FAILED sessionId={} reason={}", sessionId, e.getMessage());
        }
        log.info("COMPACTION sessionId={} compressed={} kept={} summaryChars={}",
                sessionId, prefix.size(), tail.size(), summary.length());
        return new CompactionOutcome(tail, summary, true);
    }

    /**
     * 是否触发压缩：条数与估算 token 取 OR（AC-401-9）。
     * 抽成包可见方法便于单测覆盖 1 / 12 / 13 / 29 / 30 / 31 等边界。
     */
    boolean needsCompaction(int messageCount, int estimatedTokens) {
        return messageCount > properties.getCompactionTriggerMessages()
                || estimatedTokens > properties.getCompactionTriggerTokens();
    }

    /** 只统计真正会进入上下文的消息，口径与 {@code ChatContextAssembler} 一致。 */
    int estimateTokens(List<Message> messages, String summary) {
        List<String> texts = new ArrayList<>();
        for (Message message : messages) {
            if ("SUCCESS".equals(message.getStatus()) && message.getContent() != null) {
                texts.add(message.getContent());
            }
        }
        int textTokens = TokenEstimator.estimateAll(texts) + TokenEstimator.estimate(summary);
        // CR-037：开关开启时把策略选中的历史图片按固定额度计入，避免 12000 阈值失真
        if (imagePolicy == null || !features.isEnabled(S2Feature.HISTORY_IMAGE)) {
            return textTokens;
        }
        return textTokens + imagePolicy.estimatedImageTokens(imagePolicy.select(messages));
    }

    /** 模型通过 memory_save 工具把值得长期记住的事实追加到当日 L1。 */
    private void flushToDaily(List<Message> prefix,
                              ModelConfigResolver.ResolvedModelConfig config) {
        try {
            String rendered = render(prefix);
            if (rendered.isBlank()) {
                return;
            }
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(memoryTools);
            var agent = agentFactory.createNonStreaming(AgentPromptTemplates.MEMORY_FLUSH, config, toolkit);
            Msg prompt = Msg.builder().role(MsgRole.USER).textContent(rendered).build();
            agent.call(List.of(prompt)).block(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.warn("MEMORY_FLUSH_FAILED reason={}",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private String summarize(String previous, List<Message> prefix,
                             ModelConfigResolver.ResolvedModelConfig config) {
        try {
            StringBuilder input = new StringBuilder();
            if (previous != null && !previous.isBlank()) {
                input.append("【已有摘要】\n").append(previous).append("\n\n");
            }
            input.append("【新增对话】\n").append(render(prefix));
            var agent = agentFactory.createNonStreaming(AgentPromptTemplates.COMPACTION_SUMMARY, config);
            Msg prompt = Msg.builder().role(MsgRole.USER).textContent(input.toString()).build();
            Msg result = agent.call(List.of(prompt)).block(Duration.ofSeconds(30));
            return result == null ? null : result.getTextContent();
        } catch (Exception e) {
            log.warn("COMPACTION_SUMMARY_FAILED reason={}",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return null;
        }
    }

    private static List<Message> after(List<Message> history, Long uptoMessageId) {
        if (uptoMessageId == null) {
            return history;
        }
        return history.stream()
                .filter(message -> message.getId() != null && message.getId() > uptoMessageId)
                .toList();
    }

    private static String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = "ASSISTANT".equals(message.getRole()) ? "助手" : "用户";
            sb.append("[").append(role).append("] ")
                    .append(message.getContent().trim()).append('\n');
            if (sb.length() >= MAX_RENDER_CHARS) {
                break;
            }
        }
        return sb.length() > MAX_RENDER_CHARS
                ? sb.substring(0, MAX_RENDER_CHARS) : sb.toString();
    }

    /**
     * 压缩后的上下文。
     *
     * @param history   已经过阈值控制的待装配历史
     * @param summary   会话摘要，可为空
     * @param fullWindow true 表示这段历史可直接全部带入上下文（压缩开关开启且已按阈值收敛）；
     *                   false 表示装配层应回退 S1 的 12 条硬截断（压缩开关关闭）
     */
    public record CompactionOutcome(List<Message> history, String summary, boolean fullWindow) {
        public CompactionOutcome {
            history = Objects.requireNonNullElse(history, List.of());
        }
    }
}
