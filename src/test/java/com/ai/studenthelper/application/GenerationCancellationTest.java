package com.ai.studenthelper.application;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CR-053 / AC-518-1：取消句柄的 dispose / 唤醒 / 竞态。 */
class GenerationCancellationTest {

    @Test
    void cancelDisposesSubscriptionAndWakesAwait() throws Exception {
        GenerationCancellation cancellation = new GenerationCancellation();
        Disposable subscription = mock(Disposable.class);
        cancellation.bind(subscription);

        AtomicBoolean awake = new AtomicBoolean(false);
        Thread waiter = new Thread(() -> {
            try {
                awake.set(cancellation.await(3000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();
        Thread.sleep(30); // 确保等待线程已进入 await
        cancellation.cancel();
        waiter.join(2000);

        assertFalse(waiter.isAlive(), "cancel 必须唤醒 latch.await");
        assertTrue(awake.get());
        assertTrue(cancellation.isCancelled());
        verify(subscription, atLeastOnce()).dispose();
    }

    @Test
    void cancelBeforeBindDisposesAsSoonAsSubscriptionAppears() {
        GenerationCancellation cancellation = new GenerationCancellation();
        cancellation.cancel();
        Disposable subscription = mock(Disposable.class);
        cancellation.bind(subscription);
        verify(subscription).dispose();
        assertTrue(cancellation.isCancelled());
    }

    @Test
    void cancelIsIdempotent() {
        GenerationCancellation cancellation = new GenerationCancellation();
        Disposable subscription = mock(Disposable.class);
        cancellation.bind(subscription);
        cancellation.cancel();
        cancellation.cancel();
        // cancel() 内 dispose 一次 + bind 竞态未触发，共一次
        verify(subscription, times(1)).dispose();
    }

    @Test
    void finishAlsoWakesAwait() throws Exception {
        GenerationCancellation cancellation = new GenerationCancellation();
        assertTrue(cancellation.await(0) == false, "未完成时应超时");
        cancellation.finish();
        assertTrue(cancellation.await(1000), "正常结束也要唤醒等待");
        assertFalse(cancellation.isCancelled());
    }
}
