package com.ai.studenthelper.infra.storage;

import com.ai.studenthelper.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

/**
 * 图片落盘：`./data/images/<sessionId>/<uuid>.<ext>`，DB 只存相对路径（技术规范 5.4）。
 */
@Component
public class ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

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
