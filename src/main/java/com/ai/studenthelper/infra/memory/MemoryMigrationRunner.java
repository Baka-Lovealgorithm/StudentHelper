package com.ai.studenthelper.infra.memory;

import com.ai.studenthelper.application.ProfileJsonStore;
import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.config.MemoryProperties;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * T-401 / AC-401-7：把 S2 的 {@code t_memory} 与画像 {@code depth} 一次性迁入 md。
 *
 * <p>幂等：状态文件记录已迁到的最大 {@code t_memory.id} 与 depth 哈希，重启不会重复追加；
 * 只在 {@code memory} 开关开启时执行，任何异常都只记 warn，不影响启动。</p>
 */
@Component
public class MemoryMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MemoryMigrationRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MemoryFileStore store;
    private final MemoryProperties properties;
    private final S2FeatureService features;
    private final ProfileJsonStore profileStore;
    private final JdbcTemplate jdbcTemplate;

    public MemoryMigrationRunner(MemoryFileStore store, MemoryProperties properties,
                                 S2FeatureService features, ProfileJsonStore profileStore,
                                 JdbcTemplate jdbcTemplate) {
        this.store = store;
        this.properties = properties;
        this.features = features;
        this.profileStore = profileStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!features.isEnabled(S2Feature.MEMORY)) {
            return;
        }
        try {
            migrate();
        } catch (Exception e) {
            log.warn("MEMORY_MIGRATION_FAILED reason={}",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    void migrate() {
        State state = loadState();
        long lastId = state.lastMemoryId();

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, content, source_session_id FROM t_memory "
                            + "WHERE enabled = 1 AND id > ? ORDER BY id", lastId);
        } catch (Exception e) {
            log.debug("MEMORY_MIGRATION_SKIP reason=t_memory 不可用");
            return;
        }
        for (Map<String, Object> row : rows) {
            String content = row.get("content") == null ? null : String.valueOf(row.get("content"));
            Number id = (Number) row.get("id");
            Number sessionId = (Number) row.get("source_session_id");
            if (content != null && !content.isBlank()) {
                store.appendDaily(content, sessionId == null ? null : sessionId.longValue());
                if (id != null) {
                    lastId = Math.max(lastId, id.longValue());
                }
            }
        }

        String depth = profileDepth();
        String depthHash = depth == null ? "" : sha(depth);
        if (depth != null && !depth.isBlank() && !depthHash.equals(state.depthHash())) {
            MemoryFileStore.MemorySections sections = store.read();
            String appended = sections.user().isBlank()
                    ? "## 讲解偏好\n- " + depth.trim()
                    : sections.user() + "\n\n## 讲解偏好\n- " + depth.trim();
            store.updateUserSection(appended, null);
        }

        saveState(new State(lastId, depthHash));
        if (!rows.isEmpty() || !depthHash.equals(state.depthHash())) {
            log.info("MEMORY_MIGRATION_DONE memories={} depthMigrated={}", rows.size(),
                    !depthHash.equals(state.depthHash()));
        }
    }

    private String profileDepth() {
        try {
            Object depth = profileStore.read().get("depth");
            return depth instanceof String text ? text : null;
        } catch (Exception e) {
            return null;
        }
    }

    private State loadState() {
        Path file = stateFile();
        if (!Files.exists(file)) {
            return new State(0L, "");
        }
        try {
            State state = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), State.class);
            return state == null ? new State(0L, "") : state;
        } catch (Exception e) {
            return new State(0L, "");
        }
    }

    private void saveState(State state) {
        try {
            Path file = stateFile();
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), ".migration-", ".tmp");
            Files.writeString(temp, MAPPER.writeValueAsString(state), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception atomicUnsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            log.warn("MEMORY_MIGRATION_STATE_SAVE_FAILED reason={}", e.getMessage());
        }
    }

    private Path stateFile() {
        return Paths.get(properties.getDir()).toAbsolutePath().normalize().resolve(".migration.json");
    }

    private static String sha(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private record State(long lastMemoryId, String depthHash) {
    }
}
