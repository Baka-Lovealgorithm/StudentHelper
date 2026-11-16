package com.ai.studenthelper.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CR-053 / AC-518-1：会话级运行态注册表。 */
class ActiveGenerationRegistryTest {

    private static ActiveGenerationRegistry.Handle handle() {
        return mock(ActiveGenerationRegistry.Handle.class);
    }

    @Test
    void registerCancelAndUnregister() {
        ActiveGenerationRegistry registry = new ActiveGenerationRegistry();
        ActiveGenerationRegistry.Handle first = handle();
        assertTrue(registry.register(7L, first));
        assertEquals(1, registry.size());

        assertTrue(registry.cancel(7L));
        verify(first).cancel();
        assertTrue(registry.cancel(7L), "重复取消应命中且不抛错");
        verify(first, times(2)).cancel();

        registry.unregister(7L, first);
        assertEquals(0, registry.size());
        assertFalse(registry.cancel(7L), "已注销后应返回 false");
    }

    @Test
    void missingSessionReturnsFalse() {
        ActiveGenerationRegistry registry = new ActiveGenerationRegistry();
        assertFalse(registry.cancel(999L));
    }

    @Test
    void concurrentRegisterDoesNotOverwriteAndUnregisterDoesNotRemoveNewHandle() {
        ActiveGenerationRegistry registry = new ActiveGenerationRegistry();
        ActiveGenerationRegistry.Handle first = handle();
        ActiveGenerationRegistry.Handle second = handle();
        assertTrue(registry.register(7L, first));
        assertFalse(registry.register(7L, second), "同一会话只接受一个句柄");
        assertTrue(registry.cancel(7L));
        verify(first).cancel();
        verify(second, never()).cancel();

        // 旧句柄的注销不能误删注册表里新的句柄
        registry.unregister(7L, first);
        ActiveGenerationRegistry.Handle third = handle();
        assertTrue(registry.register(7L, third));
        registry.unregister(7L, second);
        assertEquals(1, registry.size());
        assertTrue(registry.cancel(7L));
        verify(third).cancel();
    }
}
