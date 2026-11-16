package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.UsageDailyResponse;
import com.ai.studenthelper.common.dto.UsageSummaryResponse;
import com.ai.studenthelper.infra.persistence.UsageSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T-206 + CR-014 用量看板：空数据不 500（AC-206-4）、窗口白名单与按天分桶（AC-206-5）。 */
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
        when(repository.aggregate(0)).thenReturn(null);
        when(repository.daily(0)).thenReturn(null);
        var result = service.summary(null);

        assertTrue(result.rows().isEmpty());
        assertTrue(result.daily().isEmpty());
        assertEquals(0, result.days());
    }

    @Test
    void rowsArePassedThroughWithoutAmountConversion() {
        when(repository.aggregate(0)).thenReturn(List.of(
                new UsageSummaryResponse("stub-model", 3L, 2L, 1L, 900L, 1200L, 8500L)));
        when(repository.daily(0)).thenReturn(List.of());
        var rows = service.summary(0).rows();

        assertEquals(1, rows.size());
        assertEquals("stub-model", rows.get(0).modelName());
        assertEquals(3L, rows.get(0).calls());
        assertEquals(2L, rows.get(0).successCalls());
        assertEquals(1L, rows.get(0).failedCalls());
        assertEquals(900L, rows.get(0).promptTokens());
        assertEquals(1200L, rows.get(0).completionTokens());
        assertEquals(8500L, rows.get(0).avgDurationMs());
    }

    @Test
    void windowIsForwardedAndDailyIsReturned() {
        when(repository.aggregate(7)).thenReturn(List.of());
        when(repository.daily(7)).thenReturn(List.of(
                new UsageDailyResponse("2026-11-10", 2L, 2L, 0L, 100L, 200L)));
        var result = service.summary(7);

        assertEquals(7, result.days());
        assertEquals(1, result.daily().size());
        assertEquals("2026-11-10", result.daily().get(0).day());
        assertEquals(2L, result.daily().get(0).calls());
        verify(repository).aggregate(7);
        verify(repository).daily(7);
    }

    @Test
    void invalidWindowIsRejected() {
        BusinessException five = assertThrows(BusinessException.class, () -> service.summary(5));
        assertEquals(ErrorCode.E1004, five.getErrorCode());
        assertThrows(BusinessException.class, () -> service.summary(-1));
    }
}
