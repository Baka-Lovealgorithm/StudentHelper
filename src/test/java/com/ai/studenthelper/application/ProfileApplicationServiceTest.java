package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.MemoryRequest;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.MemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileApplicationServiceTest {

    @Mock private ProfileJsonStore store;
    @Mock private MemoryRepository memoryRepository;
    @Mock private S2FeatureService features;

    private ProfileApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProfileApplicationService(store, memoryRepository, features);
    }

    @Test
    void disabledFeatureRejectsWrite() {
        doThrow(new BusinessException(ErrorCode.E3004)).when(features).requireEnabled(S2Feature.MEMORY);
        assertThrows(BusinessException.class,
                () -> service.addMemory(new MemoryRequest("PREFERENCE", "讲通俗些", 1L)));
        verify(memoryRepository, never()).insert(any(), any(), any());
    }

    @Test
    void injectedMemoriesAreCappedAndTruncated() {
        when(features.isEnabled(S2Feature.MEMORY)).thenReturn(true);
        when(memoryRepository.findEnabledContents(3)).thenReturn(List.of(
                "A".repeat(120), "偏好二", "偏好三", "偏好四"));
        List<String> snippets = service.enabledMemorySnippets();
        assertEquals(3, snippets.size());
        assertEquals(60, snippets.get(0).length());
        assertEquals("偏好三", snippets.get(2));
    }

    @Test
    void disabledFeatureInjectsNothing() {
        when(features.isEnabled(S2Feature.MEMORY)).thenReturn(false);
        assertTrue(service.enabledMemorySnippets().isEmpty());
        verifyNoInteractions(memoryRepository);
    }

    @Test
    void updateProfileKeepsOptionalSectionKey() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("depth", "通俗");
        existing.put("optionalSections", Map.of("code", true));
        when(store.read()).thenReturn(existing);
        service.updateProfile(Map.of("subject", "数据结构", "optionalSections", "SHOULD_BE_IGNORED"));
        verify(store).write(argThat(data -> "数据结构".equals(data.get("subject"))
                && data.get("optionalSections") instanceof Map));
    }

    @Test
    void clearDisablesAllMemories() {
        service.clearProfile();
        verify(store).clear();
        verify(memoryRepository).disableAll();
    }
}
