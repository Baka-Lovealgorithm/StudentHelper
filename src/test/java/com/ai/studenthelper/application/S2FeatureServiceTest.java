package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.FeatureToggleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DD-01：逐功能独立开关、默认关闭、关闭态拒绝并给出 E3004。 */
@ExtendWith(MockitoExtension.class)
class S2FeatureServiceTest {

    @Mock
    private FeatureToggleRepository repository;

    @Test
    void listReturnsAllNineFeaturesAndDefaultsToClosedWhenRowMissing() {
        when(repository.findAll()).thenReturn(Map.of("usagePanel", true));
        var rows = new S2FeatureService(repository).list();

        assertEquals(9, rows.size());
        assertTrue(rows.stream().anyMatch(r -> r.key().equals("usagePanel") && r.enabled()));
        assertTrue(rows.stream().filter(r -> !r.key().equals("usagePanel"))
                .allMatch(r -> !r.enabled()), "数据库缺行时按关闭处理");
    }

    @Test
    void requireEnabledThrowsE3004WhenClosed() {
        when(repository.isEnabled("knowledgeExtract")).thenReturn(false);
        var service = new S2FeatureService(repository);

        assertFalse(service.isEnabled(S2Feature.KNOWLEDGE_EXTRACT));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireEnabled(S2Feature.KNOWLEDGE_EXTRACT));
        assertEquals(ErrorCode.E3004, ex.getErrorCode());
    }

    @Test
    void requireEnabledPassesWhenOpen() {
        when(repository.isEnabled("feedback")).thenReturn(true);
        new S2FeatureService(repository).requireEnabled(S2Feature.FEEDBACK);
    }

    @Test
    void updateRejectsUnknownKeyAndPersistsKnownKey() {
        var service = new S2FeatureService(repository);

        assertThrows(BusinessException.class, () -> service.update("noSuchFeature", true));
        var row = service.update("practice", true);
        assertEquals("practice", row.key());
        assertTrue(row.enabled());
        verify(repository).setEnabled("practice", true);
    }
}
