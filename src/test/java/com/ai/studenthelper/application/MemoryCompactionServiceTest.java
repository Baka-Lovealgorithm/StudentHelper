package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.MemoryTools;
import com.ai.studenthelper.agent.SolverAgentFactory;
import com.ai.studenthelper.config.MemoryProperties;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.memory.SessionSummaryStore;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TCR-040：对话压缩的阈值判定与空档消除。
 * AC-401-8（窗口 = 触发阈值）/ AC-401-9（条数与 token 取 OR）/ AC-401-11（独立开关）。
 */
@ExtendWith(MockitoExtension.class)
class MemoryCompactionServiceTest {

    @Mock private S2FeatureService features;
    @Mock private SessionSummaryStore summaryStore;
    @Mock private MemoryTools memoryTools;
    @Mock private SolverAgentFactory agentFactory;

    private MemoryProperties properties;
    private MemoryCompactionService service;
    private ModelConfigResolver.ResolvedModelConfig config;

    @BeforeEach
    void setUp() {
        properties = new MemoryProperties();
        service = new MemoryCompactionService(properties, features, summaryStore, memoryTools, agentFactory);
        config = new ModelConfigResolver.ResolvedModelConfig(
                1L, "http://127.0.0.1:1/v1", "k", "m", false, false, null, false);
    }

    private static List<Message> history(int count, String content) {
        List<Message> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Message m = new Message();
            m.setId((long) (i + 1));
            m.setRole(i % 2 == 0 ? "USER" : "ASSISTANT");
            m.setContent(content);
            m.setStatus("SUCCESS");
            list.add(m);
        }
        return list;
    }

    // AC-401-11：压缩开关关闭时 fullWindow=false，装配层回退 S1 的 12 条硬截断
    @Test
    void featureOffReturnsOriginalHistoryWithFullWindowFalse() {
        when(features.isEnabled(S2Feature.CONVERSATION_COMPACTION)).thenReturn(false);
        List<Message> history = history(40, "题");

        MemoryCompactionService.CompactionOutcome outcome =
                service.compactIfNeeded(9L, history, config);

        assertEquals(40, outcome.history().size());
        assertFalse(outcome.fullWindow());
        assertNull(outcome.summary());
        verifyNoInteractions(summaryStore, agentFactory);
    }

    // AC-401-8：13~30 条区间必须整段带入，且不得调用模型
    @Test
    void messagesBetweenWindowAndThresholdAreKeptWithoutCompaction() {
        when(features.isEnabled(S2Feature.CONVERSATION_COMPACTION)).thenReturn(true);
        when(summaryStore.read(9L)).thenReturn(Optional.empty());

        for (int size : new int[]{1, 12, 13, 29, 30}) {
            MemoryCompactionService.CompactionOutcome outcome =
                    service.compactIfNeeded(9L, history(size, "短消息"), config);

            assertEquals(size, outcome.history().size(), size + " 条历史应全部带入，不得丢弃");
            assertTrue(outcome.fullWindow(), size + " 条时 fullWindow 应为 true");
        }
        verifyNoInteractions(agentFactory);
    }

    // AC-401-9：条数未超但 token 超阈值同样触发（走压缩流程），且失败时降级不丢历史
    @Test
    void tokenThresholdAloneTriggersCompaction() {
        when(features.isEnabled(S2Feature.CONVERSATION_COMPACTION)).thenReturn(true);
        when(summaryStore.read(9L)).thenReturn(Optional.empty());
        // 10 条 × 2000 字符 ≈ 13334 token > 12000，但条数 10 < 30
        List<Message> history = history(10, "字".repeat(2000));
        when(agentFactory.createNonStreaming(anyString(), any())).thenReturn(null);
        when(agentFactory.createNonStreaming(anyString(), any(), any())).thenReturn(null);

        MemoryCompactionService.CompactionOutcome outcome =
                service.compactIfNeeded(9L, history, config);

        verify(agentFactory).createNonStreaming(anyString(), any(), any()); // 已发起 L1 写入
        verify(agentFactory).createNonStreaming(anyString(), any());        // 已发起摘要
        // 模型不可用时降级：整段带入、不写摘要
        assertEquals(10, outcome.history().size());
        assertTrue(outcome.fullWindow());
        assertNull(outcome.summary());
        verify(summaryStore, never()).write(anyLong(), anyString(), anyLong());
    }

    @Test
    void needsCompactionUsesMessageAndTokenThresholdsWithOr() {
        // 条数边界：30 不触发、31 触发
        assertFalse(service.needsCompaction(30, 0));
        assertTrue(service.needsCompaction(31, 0));
        // token 边界：12000 不触发、12001 触发
        assertFalse(service.needsCompaction(1, 12000));
        assertTrue(service.needsCompaction(1, 12001));
        // 任一超限即触发
        assertTrue(service.needsCompaction(31, 12000));
        assertTrue(service.needsCompaction(30, 12001));
    }

    // AC-401-9：token 估算只统计 SUCCESS 且 content 非空的消息，与装配口径一致
    @Test
    void tokenEstimateIgnoresFailedAndEmptyMessages() {
        List<Message> list = new ArrayList<>(history(3, "字".repeat(825)));
        Message failed = new Message();
        failed.setContent("字".repeat(8250));
        failed.setStatus("FAILED");
        list.add(failed);
        Message empty = new Message();
        empty.setContent("");
        empty.setStatus("SUCCESS");
        list.add(empty);

        assertEquals(3 * 550 + 0, service.estimateTokens(list, null));
        assertEquals(3 * 550 + 400, service.estimateTokens(list, "字".repeat(600)));
    }
}
