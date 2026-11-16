package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.AgentPromptTemplates;
import com.ai.studenthelper.agent.SolverAgentFactory;
import com.ai.studenthelper.config.MemoryProperties;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.memory.MemoryFileStore;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

/**
 * T-401 / AC-401-5：Consolidation 定时合并。
 *
 * <p>整点检查；每天最多 {@code consolidationMaxPerDay} 次且两次成功合并至少间隔
 * {@code consolidationMinGapHours} 小时；自上次成功后无新增流水账则跳过且不调用模型。
 * 只重写 MODEL 区，USER 区字节不变；输出不合法或调用失败时保持旧文件与水位。</p>
 */
@Service
public class MemoryConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MemoryFileStore store;
    private final MemoryProperties properties;
    private final S2FeatureService features;
    private final ModelConfigResolver configResolver;
    private final SolverAgentFactory agentFactory;

    public MemoryConsolidationService(MemoryFileStore store, MemoryProperties properties,
                                      S2FeatureService features, ModelConfigResolver configResolver,
                                      SolverAgentFactory agentFactory) {
        this.store = store;
        this.properties = properties;
        this.features = features;
        this.configResolver = configResolver;
        this.agentFactory = agentFactory;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduled() {
        ConsolidationResult result = consolidate(false);
        if (!result.skipped()) {
            log.info("MEMORY_CONSOLIDATE status={} detail={}", result.status(), result.detail());
        }
    }

    /** @param force 手动触发时跳过次数与间隔限制（仍要求开关开启且有新增内容）。 */
    public ConsolidationResult consolidate(boolean force) {
        if (!features.isEnabled(S2Feature.MEMORY)) {
            return ConsolidationResult.skipped("开关关闭");
        }
        State state = loadState();
        String today = LocalDate.now().toString();
        if (!today.equals(state.date())) {
            state = new State(today, 0, state.lastSuccessEpochMs(), state.lastDailyHash());
        }
        long now = Instant.now().toEpochMilli();
        if (!force) {
            if (state.count() >= properties.getConsolidationMaxPerDay()) {
                return ConsolidationResult.skipped("今日已达 " + properties.getConsolidationMaxPerDay() + " 次上限");
            }
            long gapMs = properties.getConsolidationMinGapHours() * 3600_000L;
            if (state.lastSuccessEpochMs() > 0 && now - state.lastSuccessEpochMs() < gapMs) {
                return ConsolidationResult.skipped("距上次合并不足 " + properties.getConsolidationMinGapHours() + " 小时");
            }
        }

        String daily = readRecentDaily(properties.getConsolidationLookbackDays());
        if (daily.isBlank()) {
            return ConsolidationResult.skipped("最近没有流水账");
        }
        MemoryFileStore.MemorySections sections = store.read();
        String watermark = sha(daily + "\n---\n" + sections.model());
        if (watermark.equals(state.lastDailyHash())) {
            return ConsolidationResult.skipped("自上次合并后无新增内容");
        }

        String merged = merge(sections.model(), daily);
        if (merged == null || merged.isBlank()) {
            return ConsolidationResult.failed("模型未返回可用内容，保持原文件");
        }
        int limit = properties.getInjectMaxChars();
        if (merged.length() > limit) {
            merged = merged.substring(0, limit);
        }
        try {
            store.replaceModelSection(merged, sections.version());
        } catch (MemoryFileStore.MemoryConflictException e) {
            return ConsolidationResult.failed("写入冲突，本次放弃：" + e.getMessage());
        } catch (Exception e) {
            return ConsolidationResult.failed("写入失败：" + e.getMessage());
        }

        saveState(new State(today, state.count() + 1, now, watermark));
        return ConsolidationResult.done("已合并 " + daily.length() + " 字符流水账，MODEL 区 " + merged.length() + " 字符");
    }

    private String merge(String currentModel, String daily) {
        try {
            String input = "【当前长期记忆】\n" + (currentModel.isBlank() ? "（空）" : currentModel)
                    + "\n\n【最近流水账】\n" + daily;
            // CR-043：全局任务无会话上下文，resolveForSession(null) → 全局默认模型（仍保留旧表兜底）
            var config = configResolver.resolveForSession(null);
            var agent = agentFactory.createNonStreaming(AgentPromptTemplates.MEMORY_CONSOLIDATE, config);
            Msg prompt = Msg.builder().role(MsgRole.USER).textContent(input).build();
            Msg result = agent.call(List.of(prompt)).block(Duration.ofSeconds(45));
            String text = result == null ? null : result.getTextContent();
            return text == null ? null : stripFence(text);
        } catch (Exception e) {
            log.warn("MEMORY_CONSOLIDATE_FAILED reason={}",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return null;
        }
    }

    private String readRecentDaily(int days) {
        StringBuilder sb = new StringBuilder();
        List<String> names = store.listDailyNames();
        for (int i = 0; i < Math.min(days, names.size()); i++) {
            String name = names.get(i);
            sb.append("## ").append(name).append('\n')
                    .append(store.readByName(name)).append("\n\n");
        }
        return sb.toString().trim();
    }

    private State loadState() {
        Path file = stateFile();
        if (!Files.exists(file)) {
            return new State(LocalDate.now().toString(), 0, 0L, "");
        }
        try {
            State state = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), State.class);
            return state == null ? new State(LocalDate.now().toString(), 0, 0L, "") : state;
        } catch (Exception e) {
            return new State(LocalDate.now().toString(), 0, 0L, "");
        }
    }

    private void saveState(State state) {
        try {
            Path file = stateFile();
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), ".consolidate-", ".tmp");
            Files.writeString(temp, MAPPER.writeValueAsString(state), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception atomicUnsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            log.warn("MEMORY_CONSOLIDATE_STATE_SAVE_FAILED reason={}", e.getMessage());
        }
    }

    private Path stateFile() {
        return Paths.get(properties.getDir()).toAbsolutePath().normalize().resolve(".consolidation.json");
    }

    private static String stripFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLine = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLine < 0 || lastFence <= firstLine) {
            return trimmed.replace("```", "").trim();
        }
        return trimmed.substring(firstLine + 1, lastFence).trim();
    }

    private static String sha(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private record State(String date, int count, long lastSuccessEpochMs, String lastDailyHash) {
    }

    /** 一次合并的结果，供接口与日志使用。 */
    public record ConsolidationResult(String status, boolean skipped, String detail) {
        public static ConsolidationResult skipped(String detail) {
            return new ConsolidationResult("SKIPPED", true, detail);
        }
        public static ConsolidationResult done(String detail) {
            return new ConsolidationResult("DONE", false, detail);
        }
        public static ConsolidationResult failed(String detail) {
            return new ConsolidationResult("FAILED", false, detail);
        }
    }
}
