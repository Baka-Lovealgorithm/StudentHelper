package com.ai.studenthelper.application;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * CR-053：会话级「运行中生成」注册表，供停止按钮按 sessionId 取消。
 *
 * <p>一个会话同时只允许一个句柄进注册表（{@code putIfAbsent}）；未注册成功的请求仍会正常生成，
 * 只是不响应取消——避免两个标签页并发提交时互相串台。请求结束由 Controller 在
 * {@code whenComplete} 里用 {@code remove(key, value)} 注销，不会误删后注册的新句柄。</p>
 */
@Component
public class ActiveGenerationRegistry {

    /** 一次生成的取消入口；由 {@link GenerationCancellation} 实现。 */
    public interface Handle {
        void cancel();

        boolean isCancelled();
    }

    private final ConcurrentHashMap<Long, Handle> active = new ConcurrentHashMap<>();

    /** @return true 表示本次注册成功（该会话之前没有运行中的生成）。 */
    public boolean register(long sessionId, Handle handle) {
        return active.putIfAbsent(sessionId, handle) == null;
    }

    /** @return true 表示命中并已触发取消；false 表示无运行中生成或已完成。 */
    public boolean cancel(long sessionId) {
        Handle handle = active.get(sessionId);
        if (handle == null) {
            return false;
        }
        handle.cancel();
        return true;
    }

    /** 仅当注册表中的句柄仍是本次请求的句柄时才移除。 */
    public void unregister(long sessionId, Handle handle) {
        active.remove(sessionId, handle);
    }

    /** 供单测与诊断读取当前注册数；不参与业务逻辑。 */
    public int size() {
        return active.size();
    }
}
