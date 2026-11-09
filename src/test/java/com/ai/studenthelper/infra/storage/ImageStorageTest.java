package com.ai.studenthelper.infra.storage;

import com.ai.studenthelper.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 图片落盘：相对路径、随机文件名、目录边界与按会话清理（AC-104-2 / AC-113-2 / NFR-108）。 */
class ImageStorageTest {

    @TempDir
    Path tempDir;

    private ImageStorage storage() {
        AppProperties properties = new AppProperties();
        properties.getStorage().setImageDir(tempDir.toString());
        return new ImageStorage(properties);
    }

    @Test
    void savesUnderSessionDirWithRandomFileName() {
        ImageStorage storage = storage();
        byte[] bytes = "fake-image".getBytes(StandardCharsets.UTF_8);
        String first = storage.save(7L, bytes, "png");
        String second = storage.save(7L, bytes, "png");

        assertNotEquals(first, second, "文件名必须随机，不能覆盖历史图片");
        assertTrue(first.startsWith("7/"), "返回相对路径且以 sessionId 为目录：" + first);
        assertFalse(first.contains(".."));
        assertTrue(first.endsWith(".png"));
        assertTrue(Files.exists(tempDir.resolve(first)));
    }

    @Test
    void deleteSessionDirRemovesOnlyThatSession() {
        ImageStorage storage = storage();
        String kept = storage.save(8L, "a".getBytes(StandardCharsets.UTF_8), "png");
        String removed = storage.save(9L, "b".getBytes(StandardCharsets.UTF_8), "png");

        storage.deleteSessionDir(9L);

        assertFalse(Files.exists(tempDir.resolve(removed)));
        assertTrue(Files.exists(tempDir.resolve(kept)));
    }

    @Test
    void rejectsPathTraversalInExtension() {
        assertThrows(IllegalArgumentException.class,
                () -> storage().save(7L, "x".getBytes(StandardCharsets.UTF_8), "png/../../../../evil"));
    }
}
