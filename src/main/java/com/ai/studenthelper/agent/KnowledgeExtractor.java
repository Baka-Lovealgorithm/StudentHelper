package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * T-203 / CR-025：知识点随解答产出。解析本轮回答第 2 段即可知点，
 * <b>不再发起第二次模型调用</b>；格式由主 Prompt 约束为「大知识点 > 小知识点」。
 *
 * <p>解析在 SSE done 之后执行，失败静默降级，绝不使解题失败、不计入 ttftMs / durationMs。</p>
 */
@Component
public class KnowledgeExtractor {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractor.class);

    /** 每轮最多记 3 个（CR-025 用户裁决：不能抽太散）。 */
    public static final int MAX_ITEMS = 3;
    /** major 超过该长度视为整句而非知识点，丢弃。 */
    static final int MAX_MAJOR_CHARS = 20;
    /** minor 超过该长度时降级为「无小类」。 */
    static final int MAX_MINOR_CHARS = 20;
    /** subject 列宽 64。 */
    private static final int MAX_SUBJECT_CHARS = 64;

    private static final List<String> PLACEHOLDER = List.of(
            "无", "暂无", "无。", "无特别知识点", "无特别知识点。", "不涉及", "—", "-", "无知识点");

    /** 大知识点 / 小知识点；minor 可为 null。 */
    public record KpRef(String major, String minor) {
    }

    private final S2FeatureService features;
    private final KnowledgePointRepository repository;

    public KnowledgeExtractor(S2FeatureService features, KnowledgePointRepository repository) {
        this.features = features;
        this.repository = repository;
    }

    /** 供 ChatApplicationService 在 done 之后调用；内部吞掉一切异常。 */
    public void extractAndRecord(Long sessionId, Long messageId, String subject, String section) {
        if (!features.isEnabled(S2Feature.KNOWLEDGE_EXTRACT)) {
            return;
        }
        try {
            List<KpRef> refs = parseSection(section);
            if (refs.isEmpty()) {
                log.info("KNOWLEDGE_EXTRACT messageId={} subject={} items=0", messageId, subject);
                return;
            }
            String normalized = subject == null || subject.isBlank() ? "通用" : subject.trim();
            if (normalized.length() > MAX_SUBJECT_CHARS) {
                normalized = normalized.substring(0, MAX_SUBJECT_CHARS);
            }
            repository.recordHits(sessionId, messageId, normalized, refs);
            log.info("KNOWLEDGE_EXTRACT messageId={} subject={} items={}",
                    messageId, normalized, refs.size());
        } catch (Exception e) {
            log.warn("KNOWLEDGE_EXTRACT_FAILED messageId={} reason={}", messageId,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** 解析第 2 段正文：每行一条「大 > 小」，去重后取前 {@link #MAX_ITEMS} 项。 */
    public static List<KpRef> parseSection(String section) {
        if (section == null || section.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<KpRef> refs = new ArrayList<>();
        for (String rawLine : section.split("\\R")) {
            if (refs.size() >= MAX_ITEMS) {
                break;
            }
            String line = clean(rawLine);
            if (line.isEmpty() || isPlaceholder(line)) {
                continue;
            }
            KpRef ref = split(line);
            if (ref == null) {
                continue;
            }
            if (seen.add(ref.major() + "|" + (ref.minor() == null ? "" : ref.minor()))) {
                refs.add(ref);
            }
        }
        return List.copyOf(refs);
    }

    /** 去掉列表符号、粗体与行尾标点。 */
    static String clean(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        String line = rawLine.trim()
                .replace("**", "")
                .replaceAll("^(?:[-*•·]|\\d+[.、)]|[（(]\\d+[)）])\\s*", "")
                .trim();
        return line.replaceAll("[。；;，,：:]+$", "").trim();
    }

    private static boolean isPlaceholder(String line) {
        return PLACEHOLDER.contains(line) || line.length() <= 1;
    }

    /** 分隔符优先级：` > ` → `→` → `：` / `:`；左侧 major、右侧 minor。 */
    static KpRef split(String line) {
        int sepAt = line.indexOf(" > ");
        int sepLen = 3;
        if (sepAt < 0) {
            sepAt = line.indexOf('→');
            sepLen = 1;
        }
        if (sepAt < 0) {
            sepAt = line.indexOf('：');
            sepLen = 1;
        }
        if (sepAt < 0) {
            sepAt = line.indexOf(':');
            sepLen = 1;
        }
        String major;
        String minor = null;
        if (sepAt < 0) {
            major = line;
        } else {
            major = line.substring(0, sepAt).trim();
            String right = line.substring(sepAt + sepLen).trim();
            minor = right.isEmpty() ? null : right;
        }
        if (major.isEmpty() || major.length() > MAX_MAJOR_CHARS) {
            return null;
        }
        if (minor != null) {
            if (minor.length() > MAX_MINOR_CHARS) {
                minor = null;
            } else if (minor.equals(major)) {
                minor = null;
            }
        }
        return new KpRef(major, minor);
    }
}
