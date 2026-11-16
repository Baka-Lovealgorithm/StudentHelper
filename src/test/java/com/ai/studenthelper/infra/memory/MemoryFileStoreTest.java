package com.ai.studenthelper.infra.memory;

import com.ai.studenthelper.config.MemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** TCR-039 / AC-401-1 / AC-401-6：文件布局、分区隔离、CAS、原子写与路径安全。 */
class MemoryFileStoreTest {

    @TempDir
    Path tempDir;

    private MemoryFileStore store;

    @BeforeEach
    void setUp() {
        MemoryProperties properties = new MemoryProperties();
        properties.setDir(tempDir.toString());
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        store = MemoryFileStore.withClock(properties, clock);
    }

    @Test
    void initCreatesTemplateWithBothSections() {
        store.ensureLayout();
        assertTrue(Files.exists(tempDir.resolve("MEMORY.md")));
        assertTrue(Files.isDirectory(tempDir.resolve("archive")));
        MemoryFileStore.MemorySections sections = store.read();
        assertEquals("", sections.user());
        assertEquals("", sections.model());
        assertEquals("", store.mergedForInjection(2000));
    }

    @Test
    void userAndModelSectionsDoNotOverwriteEachOther() {
        MemoryFileStore.MemorySections afterUser =
                store.updateUserSection("## 我的偏好\n- 讲通俗些", null);
        MemoryFileStore.MemorySections afterModel =
                store.replaceModelSection("## 长期事实\n- 常问栈与队列", afterUser.version());

        assertEquals("## 我的偏好\n- 讲通俗些", afterModel.user());
        assertEquals("## 长期事实\n- 常问栈与队列", afterModel.model());
        assertTrue(store.mergedForInjection(2000).contains("我的偏好"));
        assertTrue(store.mergedForInjection(2000).contains("长期事实"));
    }

    @Test
    void staleVersionIsRejectedInsteadOfOverwriting() {
        MemoryFileStore.MemorySections first = store.updateUserSection("第一版", null);
        store.replaceModelSection("模型改了文件", first.version());

        assertThrows(MemoryFileStore.MemoryConflictException.class,
                () -> store.updateUserSection("用户基于旧版本写入", first.version()));
        assertEquals("第一版", store.read().user());
    }

    @Test
    void appendDailyWritesOneLinePerEntryWithSessionTag() {
        store.appendDaily("用户偏好：讲通俗些\n第二行会被压平", 22L);
        store.appendDaily("常问栈与队列", null);

        List<String> names = store.listDailyNames();
        assertEquals(List.of("2026-11-11.md"), names);

        String daily = store.readByName("2026-11-11.md");
        assertTrue(daily.startsWith("# 2026-11-11 记忆流水账"));
        assertTrue(daily.contains("[session:22] 用户偏好：讲通俗些 第二行会被压平"));
        assertTrue(daily.contains("常问栈与队列"));
        assertFalse(daily.contains("[session:null]"));
    }

    @Test
    void injectionIsTruncatedToBudgetWithHint() {
        store.updateUserSection("A".repeat(3000), null);
        String injected = store.mergedForInjection(200);
        assertEquals(200, injected.length());
        assertTrue(injected.endsWith("…（记忆已截断，可用 memory_search 检索全文）"));
    }

    @Test
    void rejectsPathTraversalAndUnknownNames() {
        assertThrows(IllegalArgumentException.class, () -> store.readByName("../secret.md"));
        assertThrows(IllegalArgumentException.class, () -> store.readByName("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> store.readByName("notes.md"));
    }

    @Test
    void clearArchivesExistingContentAndResetsTemplate() {
        store.updateUserSection("要归档的内容", null);
        store.appendDaily("流水账一条", null);

        store.clear();

        assertEquals("", store.read().user());
        assertEquals("", store.read().model());
        assertTrue(store.listDailyNames().isEmpty());
        try (var archives = Files.list(tempDir.resolve("archive"))) {
            assertTrue(archives.anyMatch(path -> path.getFileName().toString().startsWith("clear-")));
        } catch (Exception e) {
            fail("归档目录读取失败：" + e.getMessage());
        }
    }

    @Test
    void contentCannotInjectSectionMarkers() {
        store.updateUserSection("前\n<!-- MODEL:BEGIN -->\n后", null);
        String raw = store.read().raw();
        assertEquals(1, countOccurrences(raw, "<!-- MODEL:BEGIN -->"));
        assertTrue(store.read().user().contains("前"));
        assertTrue(store.read().user().contains("后"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
