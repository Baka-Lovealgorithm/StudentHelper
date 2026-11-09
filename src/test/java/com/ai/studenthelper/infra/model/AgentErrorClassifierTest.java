package com.ai.studenthelper.infra.model;

import com.ai.studenthelper.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class AgentErrorClassifierTest {

    @Test
    void timeoutBecomesE2003() {
        assertEquals(ErrorCode.E2003,
                AgentErrorClassifier.classify(new TimeoutException("stream timed out")));
    }

    @Test
    void authenticationFailureBecomesE2001() {
        assertEquals(ErrorCode.E2001,
                AgentErrorClassifier.classify(new IllegalStateException("HTTP 401 Unauthorized: invalid api key")));
        assertEquals(ErrorCode.E2001,
                AgentErrorClassifier.classify(new IOException("403 Forbidden")));
    }

    @Test
    void unreachableEndpointBecomesE2002() {
        assertEquals(ErrorCode.E2002,
                AgentErrorClassifier.classify(new ConnectException("Connection refused")));
        assertEquals(ErrorCode.E2002,
                AgentErrorClassifier.classify(new IllegalStateException("MODEL_CALL_FAILED",
                        new java.nio.channels.ClosedChannelException())));
    }

    @Test
    void safeDetailNeverContainsSecrets() {
        String detail = AgentErrorClassifier.safeDetail(
                new IOException("Authorization: Bearer sk-super-secret-key"),
                "https://example.com/v1");
        assertTrue(detail.contains("https://example.com/v1"));
        assertFalse(detail.contains("sk-super-secret-key"));
        assertFalse(detail.contains("Bearer"));
    }
}
