package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.ActiveGenerationRegistry;
import com.ai.studenthelper.application.ChatApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CR-053 / AC-518-1：停止接口按会话转发到注册表。 */
class ChatControllerCancelTest {

    private ChatController controller(ActiveGenerationRegistry registry) {
        return new ChatController(mock(ChatApplicationService.class), mock(AppProperties.class), registry);
    }

    @Test
    void cancelReturnsTrueWhenGenerationRunning() {
        ActiveGenerationRegistry registry = mock(ActiveGenerationRegistry.class);
        when(registry.cancel(7L)).thenReturn(true);

        ApiResponse<Boolean> response = controller(registry).cancel(7L);

        assertEquals("OK", response.code());
        assertTrue(response.data());
        verify(registry).cancel(7L);
    }

    @Test
    void cancelReturnsFalseWhenNothingRunning() {
        ActiveGenerationRegistry registry = mock(ActiveGenerationRegistry.class);
        when(registry.cancel(7L)).thenReturn(false);

        ApiResponse<Boolean> response = controller(registry).cancel(7L);

        assertEquals("OK", response.code());
        assertFalse(response.data());
    }
}
