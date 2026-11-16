package com.ai.studenthelper.application;

import reactor.core.Disposable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CR-053：一次生成的取消句柄。
 *
 * <p>持有 Reactor 订阅与等待 latch：{@link #cancel()} 会立即 {@code dispose()} 模型订阅并唤醒
 * {@link #await(long)}，让 {@code generate()} 停止等待、用已累积的 delta 返回半截正文；
 * {@link #bind(Disposable)} 处理「取消先于订阅完成」的竞态（此时订阅一建立就 dispose）。
 * 生成正常结束（complete / error）由 {@link #finish()} 唤醒同一个 latch。</p>
 */
public final class GenerationCancellation implements ActiveGenerationRegistry.Handle {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Disposable> subscription = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    public void bind(Disposable disposable) {
        subscription.set(disposable);
        if (cancelled.get() && disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            Disposable current = subscription.get();
            if (current != null) {
                current.dispose();
            }
            latch.countDown();
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    /** 订阅的 complete / error 回调：正常结束也唤醒等待。 */
    public void finish() {
        latch.countDown();
    }

    /** @return true 表示在超时前被唤醒（正常完成、失败或取消）。 */
    public boolean await(long timeoutMs) throws InterruptedException {
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
