package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.OptionalSectionsRequest;
import com.ai.studenthelper.domain.enums.S2Feature;
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
class OptionalSectionServiceTest {

    @Mock private ProfileJsonStore store;
    @Mock private S2FeatureService features;

    private OptionalSectionService service;

    @BeforeEach
    void setUp() {
        service = new OptionalSectionService(store, features);
    }

    @Test
    void masterSwitchOffMeansAllFalse() {
        when(features.isEnabled(S2Feature.OPTIONAL_SECTIONS)).thenReturn(false);
        assertFalse(service.current().any());
        verifyNoInteractions(store);
    }

    @Test
    void updateWritesOnlyRequestedKeysAndKeepsProfile() {
        when(features.isEnabled(S2Feature.OPTIONAL_SECTIONS)).thenReturn(true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("depth", "通俗");
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("idea", true);
        data.put("optionalSections", sections);
        when(store.read()).thenReturn(data);
        service.update(new OptionalSectionsRequest(null, null, true));
        verify(store).write(argThat(saved -> "通俗".equals(saved.get("depth"))
                && saved.get("optionalSections") instanceof Map<?, ?> m
                && Boolean.TRUE.equals(m.get("idea")) && Boolean.TRUE.equals(m.get("code"))));
    }

    @Test
    void updateOnDisabledFeatureIsRejected() {
        doThrow(new IllegalStateException("closed")).when(features)
                .requireEnabled(S2Feature.OPTIONAL_SECTIONS);
        assertThrows(IllegalStateException.class,
                () -> service.update(new OptionalSectionsRequest(true, null, null)));
        verify(store, never()).write(any());
    }
}
