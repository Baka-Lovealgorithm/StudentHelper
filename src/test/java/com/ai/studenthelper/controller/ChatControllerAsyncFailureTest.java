package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.SolutionStreamSink;
import com.ai.studenthelper.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 异步线程未捕获异常的兜底回归（TCR-014 / D 第 1 条）。
 * 改动前 CompletableFuture.runAsync 没有 exceptionally，solve 抛出的异常会被吞掉，
 * 前端只能等 SseEmitter 60s 超时且收不到任何 error 事件。
 */
class ChatControllerAsyncFailureTest {

    @Test
    void unexpectedAsyncFailureEmitsE5000() {
        SolutionStreamSink sink = mock(SolutionStreamSink.class);

        assertDoesNotThrow(() -> ChatController.emitUnexpectedFailure(
                sink, new CompletionException(new IllegalStateException("boom"))));

        verify(sink).onError(eq(ErrorCode.E5000), isNull());
    }

    @Test
    void sinkFailureDoesNotEscapeFallback() {
        SolutionStreamSink sink = mock(SolutionStreamSink.class);
        doThrow(new IllegalStateException("emitter already closed"))
                .when(sink).onError(any(), any());

        assertDoesNotThrow(() -> ChatController.emitUnexpectedFailure(
                sink, new RuntimeException("boom")));

        verify(sink).onError(eq(ErrorCode.E5000), isNull());
    }
}
