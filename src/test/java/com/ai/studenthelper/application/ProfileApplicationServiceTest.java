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
    void updateProfileWritesFields() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("depth", "通俗");
        when(store.read()).thenReturn(existing);
        service.updateProfile(Map.of("subject", "数据结构"));
        verify(store).write(argThat(data -> "数据结构".equals(data.get("subject"))
                && "通俗".equals(data.get("depth"))));
    }

    @Test
    void clearDisablesAllMemories() {
        service.clearProfile();
        verify(store).clear();
        verify(memoryRepository).disableAll();
    }


}
