package com.ai.studenthelper.infra.storage;

import com.ai.studenthelper.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 图片落盘：`./data/images/<sessionId>/<uuid>.<ext>`，DB 只存相对路径（技术规范 5.4）。
 */
@Component
public class ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> ALLOWED_EXT = Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif");

    private final Path root;

    public ImageStorage(AppProperties properties) {
        this.root = Path.of(properties.getStorage().getImageDir());
    }

    /** 保存一张图片，返回相对路径字符串。 */
    public String save(Long sessionId, byte[] bytes, String extension) {
        try {
            Path base = root.toAbsolutePath().normalize();
            Path dir = safeChild(base, String.valueOf(sessionId));
            Files.createDirectories(dir);
            String fileName = UUID.randomUUID() + "." + extension;
            Path target = safeChild(dir, fileName);
            Files.write(target, bytes);
            return base.relativize(target).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new IllegalStateException("图片保存失败：sessionId=" + sessionId, e);
        }
    }

    /**
     * CR-027：历史图片只读解析。限定文件名字符集与扩展名白名单，并保证解析后
     * 路径仍在 `./data/images/<sessionId>/` 内，杜绝 `../` 路径穿越（NFR-108）。
     */
    public Optional<Path> resolve(Long sessionId, String fileName) {
        if (sessionId == null || fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (ALLOWED_EXT.stream().noneMatch(lower::endsWith)) {
            return Optional.empty();
        }
        try {
            Path base = root.toAbsolutePath().normalize();
            Path dir = safeChild(base, String.valueOf(sessionId));
            Path target = safeChild(dir, fileName);
            return Files.isRegularFile(target) ? Optional.of(target) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 删除会话的整个图片目录；失败只记日志，不阻断响应。 */
    public void deleteSessionDir(Long sessionId) {
        Path base = root.toAbsolutePath().normalize();
        Path dir = safeChild(base, String.valueOf(sessionId));
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("图片清理失败：{}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("图片目录清理失败：{}", dir, e);
        }
    }

    /** 防路径穿越：拼接后仍必须落在 base 目录之内（NFR-108）。 */
    private static Path safeChild(Path base, String child) {
        Path target = base.resolve(child).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("图片路径越界，已拒绝");
        }
        return target;
    }
}
