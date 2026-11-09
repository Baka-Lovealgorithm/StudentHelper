package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.UsageSummaryResponse;
import com.ai.studenthelper.infra.persistence.UsageSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** T-206 用量看板：空数据不 500（AC-206-4）、聚合结果原样透传。 */
@ExtendWith(MockitoExtension.class)
class UsageApplicationServiceTest {

    @Mock
    private UsageSummaryRepository repository;
    @Mock
    private S2FeatureService features;

    private UsageApplicationService service;

    @BeforeEach
    void setUp() {
        // requireEnabled 是 void mock，默认放行；关闭态行为由 S2FeatureServiceTest 单独覆盖
        service = new UsageApplicationService(repository, features);
    }

    @Test
    void nullAggregateBecomesEmptyList() {
        when(repository.aggregate()).thenReturn(null);
        assertTrue(service.summary().isEmpty());
    }

    @Test
    void rowsArePassedThroughWithoutAmountConversion() {
        when(repository.aggregate()).thenReturn(List.of(
                new UsageSummaryResponse("stub-model", 3L, 2L, 1L, 900L, 1200L, 8500L)));
        List<UsageSummaryResponse> rows = service.summary();

        assertEquals(1, rows.size());
        assertEquals("stub-model", rows.get(0).modelName());
        assertEquals(3L, rows.get(0).calls());
        assertEquals(2L, rows.get(0).successCalls());
        assertEquals(1L, rows.get(0).failedCalls());
        assertEquals(900L, rows.get(0).promptTokens());
        assertEquals(1200L, rows.get(0).completionTokens());
        assertEquals(8500L, rows.get(0).avgDurationMs());
    }
}
