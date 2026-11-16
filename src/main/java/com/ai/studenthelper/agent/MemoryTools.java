package com.ai.studenthelper.agent;

import com.ai.studenthelper.infra.memory.MemoryFileStore;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * T-401：给模型用的长期记忆工具（命名照 Harness）。
 *
 * <p>{@code memory_save} 只追加当日 L1，不提供改 {@code MEMORY.md} 的入口；L2 由
 * Consolidation 独占维护。{@code memory_search} / {@code memory_get} 可读 L1 与 L2。</p>
 */
@Component
public class MemoryTools {

    private static final int MAX_SEARCH_HITS = 20;
    private static final int MAX_GET_CHARS = 4000;

    private final MemoryFileStore store;

    public MemoryTools(MemoryFileStore store) {
        this.store = store;
    }

    @Tool(name = "memory_save",
            description = "把值得跨会话记住的事实写入今天的记忆流水账。只追加、不改历史文件；每次只写一条，用一句话说清。",
            readOnly = false,
            concurrencySafe = false)
    public String memorySave(
            @ToolParam(name = "content", required = true,
                    description = "要记住的一条事实，例如「用户偏好讲通俗些，少用公式」") String content) {
        store.appendDaily(content, null);
        return "已写入今日记忆流水账。";
    }

    @Tool(name = "memory_search",
            description = "在长期记忆中按关键词检索。默认同时搜索 MEMORY.md 与每日流水账，返回命中行及所在文件。",
            readOnly = true,
            concurrencySafe = true)
    public String memorySearch(
            @ToolParam(name = "query", required = true, description = "关键词，按字面子串匹配，不区分大小写") String query,
            @ToolParam(name = "scope", required = false,
                    description = "检索范围：all（默认）/ memory（只搜 MEMORY.md）/ daily（只搜每日流水账）") String scope) {
        if (query == null || query.isBlank()) {
            return "检索关键词不能为空。";
        }
        String target = query.trim().toLowerCase(Locale.ROOT);
        String mode = scope == null || scope.isBlank() ? "all" : scope.trim().toLowerCase(Locale.ROOT);
        if (!List.of("all", "memory", "daily").contains(mode)) {
            return "scope 只能是 all / memory / daily。";
        }

        List<String> hits = new ArrayList<>();
        if (!"daily".equals(mode)) {
            collectHits(MemoryFileStore.MEMORY_FILE, store.readByName(MemoryFileStore.MEMORY_FILE),
                    target, hits);
        }
        if (!"memory".equals(mode)) {
            for (String name : store.listDailyNames()) {
                collectHits(name, store.readByName(name), target, hits);
                if (hits.size() >= MAX_SEARCH_HITS) {
                    break;
                }
            }
        }
        if (hits.isEmpty()) {
            return "长期记忆中没有找到包含「" + query.trim() + "」的内容。";
        }
        StringBuilder sb = new StringBuilder("命中 " + hits.size() + " 条：\n");
        hits.stream().limit(MAX_SEARCH_HITS).forEach(hit -> sb.append(hit).append('\n'));
        return sb.toString();
    }

    @Tool(name = "memory_get",
            description = "读取某个记忆文件的指定行范围。文件只能是 MEMORY.md 或 YYYY-MM-DD.md。",
            readOnly = true,
            concurrencySafe = true)
    public String memoryGet(
            @ToolParam(name = "path", required = true,
                    description = "文件名，例如 MEMORY.md 或 2026-11-11.md") String path,
            @ToolParam(name = "startLine", required = false, description = "起始行号，从 1 开始，默认 1") Integer startLine,
            @ToolParam(name = "endLine", required = false, description = "结束行号，默认 startLine + 80") Integer endLine) {
        String content;
        try {
            content = store.readByName(path);
        } catch (IllegalArgumentException e) {
            return "只能读取 MEMORY.md 或形如 2026-11-11.md 的记忆文件。";
        }
        if (content.isEmpty()) {
            return "该记忆文件不存在或为空。";
        }
        String[] lines = content.split("\n", -1);
        int from = Math.max(1, startLine == null ? 1 : startLine);
        int to = Math.max(from, endLine == null ? from + 80 : endLine);
        to = Math.min(to, lines.length);

        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to && sb.length() < MAX_GET_CHARS; i++) {
            sb.append(i).append(": ").append(lines[i - 1]).append('\n');
        }
        if (to < lines.length) {
            sb.append("… 共 ").append(lines.length).append(" 行，后续可用 startLine/endLine 继续读取。");
        }
        return sb.toString();
    }

    private static void collectHits(String name, String content, String target, List<String> hits) {
        if (content == null || content.isEmpty()) {
            return;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length && hits.size() < MAX_SEARCH_HITS; i++) {
            if (lines[i].toLowerCase(Locale.ROOT).contains(target)) {
                hits.add(name + ":" + (i + 1) + ": " + lines[i].trim());
            }
        }
    }
}
