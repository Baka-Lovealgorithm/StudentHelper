package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.ProcessItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRecorderTest {

    /** 记录型 sink：捕获推送给前端的过程事件。 */
    private static final class RecordingSink implements SolutionStreamSink {
        final List<ProcessItem> pushed = new ArrayList<>();

        @Override
        public void onProcess(ProcessItem item) {
            pushed.add(item);
        }

        @Override
        public void onStatus(com.ai.studenthelper.domain.enums.AgentStage stage) {
        }

        @Override
        public void onDelta(String text, int index) {
        }

        @Override
        public void onDone(com.ai.studenthelper.common.dto.DonePayload payload) {
        }

        @Override
        public void onError(com.ai.studenthelper.common.ErrorCode code, String detail) {
        }
    }

    private final RecordingSink sink = new RecordingSink();
    private final ProcessRecorder recorder = new ProcessRecorder(sink);

    /** AC-502-1：工具按名称前缀分三类，seq 从 1 递增。 */
    @Test
    void toolEventClassifiedByPrefix() {
        recorder.recordTool("subject_get", Map.of("code", "DATA_STRUCTURE"), "规范全文");
        recorder.recordTool("skill_get", Map.of("name", "复杂度标注"), "技能正文");
        recorder.recordTool("mcp_search", Map.of("q", "栈"), "结果");

        List<ProcessItem> items = recorder.finish();
        assertEquals(3, items.size());
        assertEquals(ProcessItem.KIND_SUBJECT, items.get(0).kind());
        assertEquals("读入学科规范：subject_get", items.get(0).title());
        assertEquals(ProcessItem.KIND_SKILL, items.get(1).kind());
        assertEquals(ProcessItem.KIND_TOOL, items.get(2).kind());
        assertEquals(List.of(1, 2, 3), items.stream().map(ProcessItem::seq).toList());
        assertEquals(3, sink.pushed.size(), "工具事件应即时推送");
    }

    /** AC-502-4：参数与结果截断，敏感串与 Base64 图片不落库。 */
    @Test
    void detailsAreSanitizedAndTruncated() {
        // Map.of 的迭代顺序在 JVM 间随机；若长 q 排在前面，sanitize 后的 apiKey=***
        // 会落在 120 字摘要之外导致断言偶发失败。此处固定顺序，只消除测试自身的随机性。
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("apiKey", "sk-123456");
        input.put("q", "长".repeat(300));
        recorder.recordTool("mcp_x", input,
                "data:image/png;base64,AAAA" + "b".repeat(400));
        ProcessItem item = recorder.finish().get(0);
        assertFalse(item.detail().contains("sk-123456"));
        assertTrue(item.detail().contains("apiKey=***"));
        assertFalse(item.detail().contains("AAAA"));
        assertTrue(item.detail().contains("[图片数据]"));
        assertFalse(item.detail().contains("长".repeat(200)), "参数摘要不超过 120 字");
    }

    /** AC-502-1 / AC-502-2：整轮思考合并为一条；节流推送至少一次。 */
    @Test
    void thinkingIsAggregatedIntoSingleItem() {
        recorder.recordThinking("a".repeat(200));
        recorder.recordThinking("b".repeat(30));
        recorder.recordThinking("");
        List<ProcessItem> items = recorder.finish();
        assertEquals(1, items.size());
        assertEquals(ProcessItem.KIND_THINKING, items.get(0).kind());
        assertEquals("思考", items.get(0).title());
        assertEquals(230, items.get(0).detail().length());
        assertEquals(1, items.get(0).seq());
        assertFalse(sink.pushed.isEmpty(), "达到 120 字阈值时应推送更新");
        assertEquals(items.get(0).seq(), sink.pushed.get(0).seq(), "推送与落库共用同一 seq");
    }

    /** 空增量不产生条目；未知工具名归为 TOOL。 */
    @Test
    void emptyChunkIgnoredAndUnknownToolIsToolKind() {
        recorder.recordThinking(null);
        recorder.recordThinking("");
        recorder.recordTool(null, Map.of(), "");
        List<ProcessItem> items = recorder.finish();
        assertEquals(1, items.size());
        assertEquals(ProcessItem.KIND_TOOL, items.get(0).kind());
        assertEquals("调用工具：unknown", items.get(0).title());
    }
}
