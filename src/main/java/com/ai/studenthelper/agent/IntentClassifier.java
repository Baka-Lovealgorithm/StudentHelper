package com.ai.studenthelper.agent;

import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.AnswerIntent;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * CR-039：回答形态判别（模型判定，替代 CR-018 的 {@code IntentRouter} 关键词规则）。
 *
 * <p>刻意做成**一次极短非流式调用**：温度 0、只允许输出一行、超时 8s。判定发生在装配上下文之前，
 * 因此会阻塞首字；任何失败（超时 / 网络 / 空输出 / 非法词 / 未配置模型）都回落 {@code SOLVE}，
 * 绝不阻断解题，也不写 {@code t_call_log}、不进用量看板。</p>
 */
@Component
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    /** 前置阻塞首字，超时远短于压缩 / 摘要的 30s。 */
    private static final int TIMEOUT_SECONDS = 8;
    private static final int MAX_HISTORY_MESSAGES = 4;
    private static final int MAX_HISTORY_CHARS = 200;
    private static final int MAX_TEXT_CHARS = 800;
    private static final int MAX_REASON_CHARS = 40;

    private final SolverAgentFactory agentFactory;

    public IntentClassifier(SolverAgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    /**
     * @param sessionId 仅用于日志
     * @param history   本轮之前的全部历史（调用方保证不含本轮用户消息）
     * @param text      本轮用户文本，可为空（纯图片题）
     * @param images    本轮图片数量
     * @param config    当前会话模型配置
     */
    public Result classify(Long sessionId, List<Message> history, String text, int images,
                          ModelConfigResolver.ResolvedModelConfig config) {
        if (config == null) {
            return fallback(sessionId, "缺少模型配置");
        }
        try {
            var agent = agentFactory.createNonStreaming(AgentPromptTemplates.INTENT_CLASSIFY, config);
            Msg prompt = Msg.builder().role(MsgRole.USER).textContent(buildInput(history, text, images)).build();
            Msg result = agent.call(List.of(prompt)).block(Duration.ofSeconds(TIMEOUT_SECONDS));
            String raw = result == null ? null : result.getTextContent();
            if (raw == null || raw.isBlank()) {
                return fallback(sessionId, "空输出");
            }
            Result parsed = parseOutput(raw, sessionId);
            log.info("INTENT_CLASSIFY sessionId={} intent={} source=model reason={}",
                    sessionId, parsed.intent(), parsed.reason());
            return parsed;
        } catch (Exception e) {
            return fallback(sessionId, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 解析模型输出：取首个合法词；非法 / 空按 SOLVE。抽成包可见便于单测覆盖脏输出。 */
    static Result parseOutput(String raw, Long sessionId) {
        if (raw == null || raw.isBlank()) {
            return new Result(AnswerIntent.SOLVE, "空输出", "fallback");
        }
        return new Result(AnswerIntent.parse(raw), reasonOf(raw), "model");
    }

    /** 输入只带最近 4 条的摘要与本轮文本，不传图片字节（分类只读文字，避免额外视觉计费）。 */
    private static String buildInput(List<Message> history, String text, int images) {
        StringBuilder sb = new StringBuilder("【上一轮对话摘要】\n");
        List<Message> recent = tail(history, MAX_HISTORY_MESSAGES);
        if (recent.isEmpty()) {
            sb.append("（无历史，本会话第一轮）\n");
        } else {
            for (Message message : recent) {
                String content = message.getContent();
                if (content == null || content.isBlank()) {
                    continue;
                }
                String role = "ASSISTANT".equals(message.getRole()) ? "助手" : "用户";
                sb.append(role).append("：").append(clip(content.trim(), MAX_HISTORY_CHARS)).append('\n');
            }
        }
        sb.append("\n【本轮用户输入】\n");
        String body = text == null ? "" : text.trim();
        sb.append(body.isEmpty() ? "（本轮无文字，仅图片）" : clip(body, MAX_TEXT_CHARS)).append('\n');
        if (images > 0) {
            sb.append("【本轮含 ").append(images).append(" 张图片】\n");
        }
        return sb.toString();
    }

    private static List<Message> tail(List<Message> history, int limit) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, history.size() - limit);
        return history.subList(from, history.size());
    }

    private static String clip(String value, int max) {
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    /** 只保留「 | 」之后的一句理由，去掉模型可能带出的前缀与换行。 */
    private static String reasonOf(String raw) {
        String firstLine = raw.trim().split("\\R", 2)[0].trim();
        int bar = firstLine.indexOf('|');
        if (bar < 0) {
            return "";
        }
        String reason = firstLine.substring(bar + 1).trim();
        return reason.length() > MAX_REASON_CHARS ? reason.substring(0, MAX_REASON_CHARS) : reason;
    }

    private static Result fallback(Long sessionId, String reason) {
        log.warn("INTENT_CLASSIFY sessionId={} intent=SOLVE source=fallback reason={}", sessionId, reason);
        return new Result(AnswerIntent.SOLVE, reason == null ? "" : reason, "fallback");
    }

    /** 判定结果：形态 + 一句理由 + 来源（model / fallback）。 */
    public record Result(AnswerIntent intent, String reason, String source) {
        public boolean fallback() {
            return "fallback".equals(source);
        }
    }
}
