package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * T-203 知识点模型抽取（DD-02）。在 SSE done 之后执行，失败静默降级，
 * 绝不使解题失败、不计入 ttftMs / durationMs。
 */
@Component
public class KnowledgeExtractor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECTION_TITLE = "## ② 涉及知识点";
    private static final int MAX_ITEMS = 8;

    /** 大知识点 / 小知识点；minor 可为 null。 */
    public record KpRef(String major, String minor) {
    }

    private final S2FeatureService features;
    private final ModelConfigResolver configResolver;
    private final SolverAgentFactory agentFactory;
    private final KnowledgePointRepository repository;

    public KnowledgeExtractor(S2FeatureService features,
                              ModelConfigResolver configResolver,
                              SolverAgentFactory agentFactory,
                              KnowledgePointRepository repository) {
        this.features = features;
        this.configResolver = configResolver;
        this.agentFactory = agentFactory;
        this.repository = repository;
    }

    /** 供 ChatApplicationService 在 done 之后调用；内部吞掉一切异常。 */
    public void extractAndRecord(Long sessionId, Long messageId, String answer) {
        if (!features.isEnabled(S2Feature.KNOWLEDGE_EXTRACT)) {
            return;
        }
        try {
            String section = sectionKnowledge(answer);
            if (section == null || section.isBlank()) {
                return;
            }
            var config = configResolver.resolveForSession(sessionId);
            var agent = agentFactory.createNonStreaming(AgentPromptTemplates.KNOWLEDGE_EXTRACT, config);
            Msg prompt = Msg.builder().role(MsgRole.USER).textContent(section).build();
            Msg result = agent.call(List.of(prompt)).block(Duration.ofSeconds(20));
            String raw = result == null ? null : result.getTextContent();
            List<KpRef> refs = parseRefs(raw, section);
            if (refs.isEmpty()) {
                log.info("KNOWLEDGE_EXTRACT messageId={} items=0", messageId);
                return;
            }
            repository.recordHits(sessionId, messageId, refs);
            log.info("KNOWLEDGE_EXTRACT messageId={} items={}", messageId, refs.size());
        } catch (Exception e) {
            log.warn("KNOWLEDGE_EXTRACT_FAILED messageId={} reason={}", messageId,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** 取六段中的 ② 段，不依赖 parse 的模式，也不抛 E2005。 */
    static String sectionKnowledge(String answer) {
        if (answer == null) {
            return null;
        }
        int start = answer.indexOf(SECTION_TITLE);
        if (start < 0) {
            return null;
        }
        int from = start + SECTION_TITLE.length();
        int end = answer.indexOf("\n## ", from);
        return answer.substring(from, end < 0 ? answer.length() : end).trim();
    }

    /** 严格 JSON 解析 + 防幻觉：知识点名字必须在 ② 段原文中有词面依据。 */
    static List<KpRef> parseRefs(String raw, String section) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            cleaned = firstNewline < 0 ? cleaned : cleaned.substring(firstNewline + 1);
            cleaned = cleaned.replace("```", "").trim();
        }
        int open = cleaned.indexOf('[');
        int close = cleaned.lastIndexOf(']');
        if (open < 0 || close <= open) {
            return List.of();
        }
        List<KpRef> refs = new ArrayList<>();
        try {
            JsonNode array = MAPPER.readTree(cleaned.substring(open, close + 1));
            if (!array.isArray()) {
                return List.of();
            }
            for (JsonNode node : array) {
                if (refs.size() >= MAX_ITEMS) {
                    break;
                }
                String major = text(node, "major");
                String minor = text(node, "minor");
                if (major == null || major.isBlank() || major.length() > 20) {
                    continue;
                }
                boolean grounded = section.contains(major)
                        || (minor != null && !minor.isBlank() && section.contains(minor));
                if (!grounded) {
                    continue;
                }
                refs.add(new KpRef(major, (minor == null || minor.isBlank()) ? null : minor));
            }
        } catch (Exception e) {
            return List.of();
        }
        return List.copyOf(refs);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText().trim();
    }
}
