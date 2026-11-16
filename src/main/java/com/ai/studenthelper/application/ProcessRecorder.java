package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.ProcessItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CR-027：单次解题请求内的过程聚合器（非 Bean）。Hook 可能在不同线程回调，
 * 因此关键状态用并发容器 / synchronized 保护；工具事件即时推送，思考增量节流后
 * 以同一 seq 覆盖推送，生成结束时合并为一条并交给 Repository 落库。
 */
public final class ProcessRecorder {

    private static final int MAX_TOOL_INPUT = 120;
    private static final int MAX_TOOL_OUTPUT = 200;
    /** CR-047：作图工具的参数就是 Mermaid 源码，按用户裁决完整保留（上限与渲染器校验一致）。 */
    private static final int MAX_DIAGRAM_INPUT = 4000;
    private static final int MAX_THINKING_CHARS = 2000;
    private static final int THINKING_FLUSH_STEP = 120;

    private final SolutionStreamSink sink;
    private final long t0 = System.currentTimeMillis();
    private final AtomicInteger seq = new AtomicInteger();
    private final List<ProcessItem> items = new CopyOnWriteArrayList<>();
    /** CR-047：本轮已生成的图片链接，收尾时用于「模型漏贴链接」的兜底补图。 */
    private final List<String> diagramLinks = new CopyOnWriteArrayList<>();
    private final StringBuilder thinkingText = new StringBuilder();
    private int thinkingSeq = -1;
    private int lastFlushedLength = 0;

    public ProcessRecorder(SolutionStreamSink sink) {
        this.sink = sink;
    }

    /** CR-047：登记一张已渲染的图片链接（由 DiagramTools 回调写入）。 */
    public void addDiagramLink(String markdownLink) {
        if (markdownLink != null && !markdownLink.isBlank()) {
            diagramLinks.add(markdownLink);
        }
    }

    /** 本轮生成的图片链接（按生成顺序）。 */
    public List<String> diagramLinks() {
        return List.copyOf(diagramLinks);
    }

    /** PostActingEvent：一次工具调用 = 一条过程，按工具名前缀区分三类。 */
    public void recordTool(String toolName, Map<String, Object> input, String outputText) {
        String name = toolName == null ? "unknown" : toolName;
        String kind = name.startsWith("subject_") ? ProcessItem.KIND_SUBJECT
                : name.startsWith("skill_") ? ProcessItem.KIND_SKILL
                : ProcessItem.KIND_TOOL;
        String label = kind.equals(ProcessItem.KIND_SUBJECT) ? "读入学科规范"
                : kind.equals(ProcessItem.KIND_SKILL) ? "读入通用技能" : "调用工具";
        // CR-047：作图工具保留完整源码与换行（用户裁决），其余工具仍按 120 字单行摘要
        String rawInput = sanitize(String.valueOf(input));
        boolean diagram = name.startsWith("diagram_");
        String inputText = diagram ? truncateKeepLines(rawInput, MAX_DIAGRAM_INPUT)
                : summarize(rawInput, MAX_TOOL_INPUT);
        String detail = "参数：" + inputText
                + "\n结果：" + summarize(sanitize(outputText), MAX_TOOL_OUTPUT);
        ProcessItem item = new ProcessItem(null, seq.incrementAndGet(), kind,
                label + "：" + name, detail, elapsed());
        items.add(item);
        sink.onProcess(item);
    }

    /** ReasoningChunkEvent：整轮思考合并成一条，按 120 字节流更新同一 seq。 */
    public void recordThinking(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (thinkingText.length() >= MAX_THINKING_CHARS) {
                return;
            }
            int room = MAX_THINKING_CHARS - thinkingText.length();
            thinkingText.append(chunk.length() > room ? chunk.substring(0, room) : chunk);
            if (thinkingSeq < 0) {
                thinkingSeq = seq.incrementAndGet();
            }
            ProcessItem item = new ProcessItem(null, thinkingSeq, ProcessItem.KIND_THINKING,
                    "思考", thinkingText.toString(), elapsed());
            replaceOrAdd(item);
            if (thinkingText.length() - lastFlushedLength >= THINKING_FLUSH_STEP) {
                lastFlushedLength = thinkingText.length();
                sink.onProcess(item);
            }
        }
    }

    /** 生成结束后返回最终快照；思考补齐最后一次更新，按 seq 升序。 */
    public List<ProcessItem> finish() {
        synchronized (this) {
            if (thinkingSeq >= 0) {
                ProcessItem last = new ProcessItem(null, thinkingSeq, ProcessItem.KIND_THINKING,
                        "思考", thinkingText.toString(), elapsed());
                replaceOrAdd(last);
            }
            List<ProcessItem> snapshot = new ArrayList<>(items);
            snapshot.sort(Comparator.comparingInt(ProcessItem::seq));
            return snapshot;
        }
    }

    private void replaceOrAdd(ProcessItem item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).seq() == item.seq()) {
                items.set(i, item);
                return;
            }
        }
        items.add(item);
    }

    private long elapsed() {
        return System.currentTimeMillis() - t0;
    }

    /** CR-047：只截断、不折叠空白，保证 Mermaid 源码按原样落库。 */
    private static String truncateKeepLines(String value, int max) {
        String v = value == null || value.equals("null") ? "" : value.strip();
        return v.length() > max ? v.substring(0, max) + "…" : v;
    }

    private static String summarize(String value, int max) {
        String v = value == null || value.equals("null") ? "" : value.replaceAll("\\s+", " ").trim();
        return v.length() > max ? v.substring(0, max) + "…" : v;
    }

    /** 只做展示，不把密钥 / Base64 图片写进过程表。 */
    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("(?i)(api[_-]?key|authorization|secret|password|token)\\s*[=:]\\s*[^,}\\s]+",
                        "$1=***")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+", "Bearer ***")
                .replaceAll("data:image/[^;]+;base64,[A-Za-z0-9+/=]+", "[图片数据]");
    }
}
