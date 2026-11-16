package com.ai.studenthelper.infra.memory;

import com.ai.studenthelper.config.MemoryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;

/**
 * T-401 / AC-401-4：保存每个会话的压缩摘要，避免每次请求重复压缩。
 * 与记忆文件同根目录下的 {@code .sessions/}，不参与 L1/L2 注入。
 */
@Component
public class SessionSummaryStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path dir;

    public SessionSummaryStore(MemoryProperties properties) {
        this.dir = Paths.get(properties.getDir()).toAbsolutePath().normalize().resolve(".sessions");
    }

    public Optional<SessionSummary> read(Long sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        Path file = fileOf(sessionId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            SessionSummary summary = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8),
                    SessionSummary.class);
            return summary != null && summary.summary() != null && !summary.summary().isBlank()
                    ? Optional.of(summary) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void write(Long sessionId, String summary, Long uptoMessageId) {
        if (sessionId == null || summary == null || summary.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(dir);
            Path target = fileOf(sessionId);
            Path temp = Files.createTempFile(dir, ".summary-", ".tmp");
            Files.writeString(temp, MAPPER.writeValueAsString(
                    new SessionSummary(summary.trim(), uptoMessageId, Instant.now())),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicUnsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存会话摘要失败", e);
        }
    }

    public void delete(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            Files.deleteIfExists(fileOf(sessionId));
        } catch (IOException e) {
            throw new UncheckedIOException("删除会话摘要失败", e);
        }
    }

    private Path fileOf(Long sessionId) {
        Path file = dir.resolve(sessionId + ".json").normalize();
        if (!file.startsWith(dir)) {
            throw new IllegalArgumentException("非法的会话 id");
        }
        return file;
    }

    /** 摘要正文 + 覆盖到的最后一条消息 id。 */
    public record SessionSummary(String summary, Long uptoMessageId, Instant updatedAt) {
    }
}
