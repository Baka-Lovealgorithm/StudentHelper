package com.ai.studenthelper.infra.log;

import com.ai.studenthelper.domain.CallLog;
import com.ai.studenthelper.infra.persistence.CallLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** CR-065：缓存命中 / 未命中 token 落库、异常值收敛与旧签名兼容。 */
class CallLogRecorderCacheTest {

    private final CallLogRepository repository = mock(CallLogRepository.class);
    private final CallLogRecorder recorder = new CallLogRecorder(repository);

    private CallLog saved() {
        ArgumentCaptor<CallLog> captor = ArgumentCaptor.forClass(CallLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void writesCacheHitAndMissOnSuccess() {
        recorder.record(11L, null, 3007, 741, 2560, 447, 1234L, "SUCCESS", null, false, null);
        CallLog log = saved();
        assertEquals(2560, log.getCacheHitTokens());
        assertEquals(447, log.getCacheMissTokens());
        assertEquals(3007, log.getPromptTokens());
        assertEquals("SUCCESS", log.getStatus());
    }

    @Test
    void clampsHitToPromptAndTreatsNullAsZero() {
        // 网关给出大于 prompt 的异常值：收敛到 prompt，避免命中率 > 100%
        recorder.record(12L, null, 100, 20, 999, 5, 10L, "SUCCESS", null, false, null);
        CallLog log = saved();
        assertEquals(100, log.getCacheHitTokens());
        assertEquals(5, log.getCacheMissTokens());
    }

    @Test
    void nullCacheValuesAreZero() {
        recorder.record(13L, null, 100, 20, null, null, 10L, "SUCCESS", null, false, null);
        CallLog log = saved();
        assertEquals(0, log.getCacheHitTokens());
        assertEquals(0, log.getCacheMissTokens());
    }

    @Test
    void legacyOverloadsStillWriteZero() {
        recorder.record(14L, null, 100, 20, 10L, "FAILED", "E2003: timeout");
        CallLog log = saved();
        assertEquals(0, log.getCacheHitTokens());
        assertEquals(0, log.getCacheMissTokens());
        assertEquals("FAILED", log.getStatus());
    }
}
