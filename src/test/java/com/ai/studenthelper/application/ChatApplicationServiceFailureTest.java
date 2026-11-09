package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.ChatContextAssembler;
import com.ai.studenthelper.agent.KnowledgeExtractor;
import com.ai.studenthelper.agent.SolutionSectionParser;
import com.ai.studenthelper.agent.S2PromptAugmenter;
import com.ai.studenthelper.agent.SolverAgentFactory;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.ChatCommand;
import com.ai.studenthelper.config.AppProperties;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.infra.log.CallLogRecorder;
import com.ai.studenthelper.infra.log.RawAnswerArchiver;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.storage.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-110 失败反馈链路回归（TCR-014 / D 第 1 条）。
 * 改动前 handleFailure 先写调用日志再 sink.onError，日志落库异常会让 error 永远发不出去，
 * 且异常逃出 CompletableFuture.runAsync 后被吞掉，客户端只能等 emitter 超时。
 */
@ExtendWith(MockitoExtension.class)
class ChatApplicationServiceFailureTest {

    @Mock private SessionApplicationService sessionService;
    @Mock private MessageRepository messageRepository;
    @Mock private ImageStorage imageStorage;
    @Mock private ModelConfigResolver configResolver;
    @Mock private SolverAgentFactory agentFactory;
    @Mock private ChatContextAssembler contextAssembler;
    @Mock private SolutionSectionParser parser;
    @Mock private CallLogRecorder logRecorder;
    @Mock private RawAnswerArchiver rawAnswerArchiver;
    @Mock private KnowledgeExtractor knowledgeExtractor;
    @Mock private SolutionStreamSink sink;
    @Mock private S2PromptAugmenter promptAugmenter;

    private ChatApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ChatApplicationService(sessionService, messageRepository, imageStorage,
                configResolver, agentFactory, contextAssembler, parser, logRecorder,
                rawAnswerArchiver, knowledgeExtractor, new AppProperties(), promptAugmenter);
        org.mockito.Mockito.lenient()
                .when(promptAugmenter.augment(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void errorIsEmittedEvenWhenStatusSaveAndCallLogBothFail() {
        ChatCommand command = new ChatCommand(1L, "题目：简述栈的特点", null, 0, List.of());
        when(sessionService.requireSession(1L)).thenReturn(new Session());
        when(configResolver.resolveForSession(1L)).thenReturn(new ModelConfigResolver.ResolvedModelConfig(
                3L, "http://127.0.0.1:1", "key", "stub", false, true, null));
        // 第一次 save 是用户消息落库，第二次是 handleFailure 里回写 FAILED 状态
        when(messageRepository.save(any(Message.class)))
                .thenAnswer(invocation -> {
                    Message m = invocation.getArgument(0);
                    m.setId(10L);
                    return m;
                })
                .thenThrow(new IllegalStateException("status save failed"));
        when(messageRepository.findBySessionId(1L)).thenThrow(new IllegalStateException("db down"));
        doThrow(new IllegalStateException("call log failed")).when(logRecorder)
                .record(any(), any(), any(), any(), anyLong(), any(), any());

        assertDoesNotThrow(() -> service.solve(command, sink));

        ArgumentCaptor<ErrorCode> code = ArgumentCaptor.forClass(ErrorCode.class);
        verify(sink).onError(code.capture(), any());
        assertNotNull(code.getValue());
        // 日志分支确实执行过且异常被吞，error 不依赖它成功
        verify(logRecorder).record(any(), any(), any(), any(), anyLong(), eq("FAILED"), any());
    }

    @Test
    void businessValidationErrorIsReportedWithItsOwnCode() {
        ChatCommand command = new ChatCommand(1L, null, null, 0, List.of());
        when(sessionService.requireSession(1L)).thenReturn(new Session());

        assertDoesNotThrow(() -> service.solve(command, sink));

        verify(sink).onError(eq(ErrorCode.E1001), any());
    }

    @Test
    void sinkFailureDoesNotEscapeHandleFailure() {
        ChatCommand command = new ChatCommand(1L, null, null, 0, List.of());
        when(sessionService.requireSession(1L)).thenReturn(new Session());
        doThrow(new IllegalStateException("emitter already closed"))
                .when(sink).onError(any(), any());

        // 即便推送失败，后续落库仍应执行且不向外抛异常（由 Controller 的 exceptionally 兜底）
        assertDoesNotThrow(() -> service.solve(command, sink));
        verify(logRecorder).record(any(), any(), any(), any(), anyLong(), eq("FAILED"), any());
    }
}
