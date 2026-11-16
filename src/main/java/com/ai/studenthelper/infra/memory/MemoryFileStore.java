package com.ai.studenthelper.infra.memory;

import com.ai.studenthelper.common.dto.MemoryOverviewResponse;
import com.ai.studenthelper.config.MemoryProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * T-401 长期记忆的唯一写入口（技术规范 5.10）。
 *
 * <p>两类文件：{@code MEMORY.md}（L2，USER / MODEL 两个标记块）与 {@code YYYY-MM-DD.md}
 * （L1，按天追加）。所有写操作在同一把进程内锁内完成「读—改—写」，并用整文件内容哈希做
 * 乐观 CAS；落盘走临时文件 + 原子替换，写前在 {@code archive/} 留一份快照。</p>
 */
@Component
public class MemoryFileStore {

    public static final String MEMORY_FILE = "MEMORY.md";
    public static final String ARCHIVE_DIR = "archive";

    private static final String USER_BEGIN = "<!-- USER:BEGIN -->";
    private static final String USER_END = "<!-- USER:END -->";
    private static final String MODEL_BEGIN = "<!-- MODEL:BEGIN -->";
    private static final String MODEL_END = "<!-- MODEL:END -->";
    private static final String TEMPLATE = """
            # 长期记忆

            本文件由「用户手写区」与「模型维护区」组成，两者互不覆盖。

            %s

            %s

            %s

            %s
            """.formatted(USER_BEGIN, USER_END, MODEL_BEGIN, MODEL_END);

    private static final Pattern DAILY_NAME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\.md");
    private static final DateTimeFormatter SNAPSHOT_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path root;
    private final Path archive;
    private final int maxEntryChars;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    @org.springframework.beans.factory.annotation.Autowired
    public MemoryFileStore(MemoryProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    private MemoryFileStore(MemoryProperties properties, Clock clock) {
        this.root = Paths.get(properties.getDir()).toAbsolutePath().normalize();
        this.archive = root.resolve(ARCHIVE_DIR);
        this.maxEntryChars = Math.max(1, properties.getMaxEntryChars());
        this.clock = clock;
    }

    /** 测试用：固定时钟，避免跨日边界导致断言不稳定。 */
    static MemoryFileStore withClock(MemoryProperties properties, Clock clock) {
        return new MemoryFileStore(properties, clock);
    }

    /** 懒创建目录与模板，幂等；不覆盖已有内容。 */
    public void ensureLayout() {
        lock.lock();
        try {
            Files.createDirectories(root);
            Files.createDirectories(archive);
            Path memory = root.resolve(MEMORY_FILE);
            if (!Files.exists(memory)) {
                atomicWrite(MEMORY_FILE, TEMPLATE);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("初始化记忆目录失败", e);
        } finally {
            lock.unlock();
        }
    }

    /** 读取当前分区内容与版本号（版本用于 CAS）。 */
    public MemorySections read() {
        lock.lock();
        try {
            ensureLayout();
            String raw = readRaw();
            return new MemorySections(userSection(raw), modelSection(raw), hash(raw), raw);
        } catch (IOException e) {
            throw new UncheckedIOException("读取长期记忆失败", e);
        } finally {
            lock.unlock();
        }
    }

    /** 供注入使用：两区合并后截断到预算；空内容返回空串。 */
    public String mergedForInjection(int maxChars) {
        MemorySections sections = read();
        String merged = (sections.user() + "\n" + sections.model()).trim();
        if (merged.isEmpty()) {
            return "";
        }
        int budget = Math.max(1, maxChars);
        if (merged.length() <= budget) {
            return merged;
        }
        String hint = "\n…（记忆已截断，可用 memory_search 检索全文）";
        int keep = Math.max(0, budget - hint.length());
        return merged.substring(0, Math.min(keep, merged.length())) + hint;
    }

    /** 用户手写区整体替换；expectedVersion 非空时做 CAS。 */
    public MemorySections updateUserSection(String content, String expectedVersion) {
        return rewriteSection(USER_BEGIN, USER_END, content, expectedVersion, true);
    }

    /** Consolidation 整体重写模型维护区；用户区字节不变。 */
    public MemorySections replaceModelSection(String content, String expectedVersion) {
        return rewriteSection(MODEL_BEGIN, MODEL_END, content, expectedVersion, false);
    }

    private MemorySections rewriteSection(String begin, String end, String content,
                                          String expectedVersion, boolean isUser) {
        lock.lock();
        try {
            ensureLayout();
            String raw = readRaw();
            String actual = hash(raw);
            if (expectedVersion != null && !expectedVersion.equals(actual)) {
                throw new MemoryConflictException("记忆文件已被其他写入者修改，请刷新后重试");
            }
            String safe = sanitize(content);
            String next = replaceBetween(raw, begin, end, safe);
            if (next.equals(raw)) {
                return new MemorySections(userSection(next), modelSection(next), actual, next);
            }
            snapshot(raw);
            atomicWrite(MEMORY_FILE, next);
            return new MemorySections(userSection(next), modelSection(next), hash(next), next);
        } catch (IOException e) {
            throw new UncheckedIOException("写入长期记忆失败", e);
        } finally {
            lock.unlock();
        }
    }

    /** 追加一条 L1 事实；sessionId 只作溯源标签，不参与分桶。 */
    public void appendDaily(String content, Long sessionId) {
        String text = oneLine(content);
        if (text.isEmpty()) {
            return;
        }
        lock.lock();
        try {
            ensureLayout();
            LocalDate today = LocalDate.now(clock);
            String name = today + ".md";
            Path file = root.resolve(name);
            if (!Files.exists(file)) {
                atomicWrite(name, "# " + today + " 记忆流水账\n\n");
            }
            String line = "- [" + LocalTime.now(clock).format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] "
                    + (sessionId == null ? "" : "[session:" + sessionId + "] ")
                    + text + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("写入每日记忆失败", e);
        } finally {
            lock.unlock();
        }
    }

    /** 面板用：MEMORY.md 与日期文件的大小与修改时间，按时间倒序。 */
    public List<MemoryOverviewResponse.MemoryFileInfo> fileInfos() {
        lock.lock();
        try {
            ensureLayout();
            List<MemoryOverviewResponse.MemoryFileInfo> infos = new ArrayList<>();
            List<String> names = new ArrayList<>(listDailyNamesUnsafe());
            names.add(0, MEMORY_FILE);
            for (String name : names) {
                Path file = resolveSafe(name);
                if (!Files.exists(file)) {
                    continue;
                }
                infos.add(new MemoryOverviewResponse.MemoryFileInfo(
                        name,
                        Files.size(file),
                        Files.getLastModifiedTime(file).toInstant().toString()));
            }
            infos.sort((a, b) -> b.updatedAt().compareTo(a.updatedAt()));
            return infos;
        } catch (IOException e) {
            throw new UncheckedIOException("列出记忆文件失败", e);
        } finally {
            lock.unlock();
        }
    }

    /** 按日期倒序列出 L1 文件名。 */
    public List<String> listDailyNames() {        lock.lock();
        try {
            ensureLayout();
            try (Stream<Path> files = Files.list(root)) {
                List<String> names = new ArrayList<>();
                files.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> DAILY_NAME.matcher(name).matches())
                        .sorted(Comparator.reverseOrder())
                        .forEach(names::add);
                return names;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("列出记忆文件失败", e);
        } finally {
            lock.unlock();
        }
    }

    /** 只允许读 MEMORY.md 与日期文件，杜绝任意路径访问。 */
    public String readByName(String name) {
        if (!MEMORY_FILE.equals(name) && !DAILY_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("非法的记忆文件名");
        }
        lock.lock();
        try {
            ensureLayout();
            Path file = resolveSafe(name);
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new UncheckedIOException("读取记忆文件失败", e);
        } finally {
            lock.unlock();
        }
    }

    /** 清空记忆：先整体归档到 archive/clear-时间戳/，再重置模板。 */
    public void clear() {
        lock.lock();
        try {
            ensureLayout();
            String stamp = LocalDateTime.now(clock).format(SNAPSHOT_STAMP);
            Path target = archive.resolve("clear-" + stamp);
            Files.createDirectories(target);
            List<String> names = new ArrayList<>(listDailyNamesUnsafe());
            names.add(MEMORY_FILE);
            for (String name : names) {
                Path source = resolveSafe(name);
                if (Files.exists(source)) {
                    Files.move(source, target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            atomicWrite(MEMORY_FILE, TEMPLATE);
        } catch (IOException e) {
            throw new UncheckedIOException("清空长期记忆失败", e);
        } finally {
            lock.unlock();
        }
    }

    private List<String> listDailyNamesUnsafe() throws IOException {
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> DAILY_NAME.matcher(name).matches())
                    .toList();
        }
    }

    private Path resolveSafe(String name) throws IOException {
        Path resolved = root.resolve(name).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("路径越界：" + name);
        }
        return resolved;
    }

    private String readRaw() throws IOException {
        Path memory = root.resolve(MEMORY_FILE);
        if (!Files.exists(memory)) {
            return TEMPLATE;
        }
        String raw = Files.readString(memory, StandardCharsets.UTF_8);
        return raw.isBlank() ? TEMPLATE : raw;
    }

    private void atomicWrite(String name, String content) throws IOException {
        Path target = resolveSafe(name);
        Path temp = Files.createTempFile(root, ".memory-", ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void snapshot(String raw) throws IOException {
        Path file = archive.resolve("MEMORY-"
                + LocalDateTime.now(clock).format(SNAPSHOT_STAMP) + ".md");
        Files.writeString(file, raw, StandardCharsets.UTF_8);
    }

    private static String userSection(String raw) {
        return slice(raw, USER_BEGIN, USER_END);
    }

    private static String modelSection(String raw) {
        return slice(raw, MODEL_BEGIN, MODEL_END);
    }

    private static String slice(String raw, String begin, String end) {
        int from = raw.indexOf(begin);
        int to = raw.indexOf(end);
        if (from < 0 || to < 0 || to < from) {
            return "";
        }
        return raw.substring(from + begin.length(), to).replace("\r\n", "\n").trim();
    }

    /** 标记缺失时把现有内容整体当作用户区，避免静默丢内容。 */
    private static String replaceBetween(String raw, String begin, String end, String content) {
        int from = raw.indexOf(begin);
        int to = raw.indexOf(end);
        if (from < 0 || to < 0 || to < from) {
            String preserved = raw.replace(USER_BEGIN, "").replace(USER_END, "")
                    .replace(MODEL_BEGIN, "").replace(MODEL_END, "").trim();
            return preserved.isEmpty()
                    ? TEMPLATE
                    : TEMPLATE.replace(USER_BEGIN, USER_BEGIN + "\n" + preserved);
        }
        String head = raw.substring(0, from + begin.length());
        String tail = raw.substring(to);
        return head + "\n" + content.trim() + "\n" + tail;
    }

    private String sanitize(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replace("\r\n", "\n").trim();
        // 防止内容里写入标记字符串破坏分区
        text = text.replace(USER_BEGIN, "").replace(USER_END, "")
                .replace(MODEL_BEGIN, "").replace(MODEL_END, "");
        if (text.length() > maxEntryChars * 4) {
            text = text.substring(0, maxEntryChars * 4);
        }
        return text;
    }

    private String oneLine(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replace("\r", " ").replace("\n", " ").trim();
        if (text.length() > maxEntryChars) {
            text = text.substring(0, maxEntryChars);
        }
        return text;
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("计算记忆版本哈希失败", e);
        }
    }

    /** 分区内容 + 版本号。 */
    public record MemorySections(String user, String model, String version, String raw) {
    }

    /** 乐观锁冲突：调用方应提示刷新后重试，而不是硬覆盖。 */
    public static class MemoryConflictException extends RuntimeException {
        public MemoryConflictException(String message) {
            super(message);
        }
    }
}
