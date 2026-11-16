package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.ChatApplicationService;
import com.ai.studenthelper.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * CR-049 / D-15：长生成超时的竞态回归。
 *
 * <p>改造前 ChatController 硬编码 new SseEmitter(60_000L)，与 ChatApplicationService 的
 * latch.await(60s) 同一刻到期：handleFailure 想发 error 时 emitter 已 complete，
 * 前端既收不到答案也收不到错误。现在 emitter 必须比 latch 晚 EMITTER_GRACE_MS。</p>
 */
class ChatControllerTimeoutTest {

    @Test
    void emitterOutlivesApplicationLatchByGraceWindow() {
        AppProperties properties = new AppProperties();
        ChatController controller = new ChatController(mock(ChatApplicationService.class), properties,
                new com.ai.studenthelper.application.ActiveGenerationRegistry());

        SseEmitter emitter = controller.stream(1L, "题目", null, 0, null, false, null);

        assertEquals(properties.getChat().getSseTimeoutMs() + ChatController.EMITTER_GRACE_MS,
                emitter.getTimeout().longValue());
    }

    @Test
    void timeoutsAreRaisedForLongGenerations() {
        AppProperties properties = new AppProperties();

        assertEquals(180_000L, properties.getChat().getSseTimeoutMs());
        assertEquals(180_000L, properties.getChat().getHardTimeoutMs());
        assertEquals(15_000L, ChatController.EMITTER_GRACE_MS);
        assertEquals(195_000L,
                properties.getChat().getSseTimeoutMs() + ChatController.EMITTER_GRACE_MS);
    }
}
