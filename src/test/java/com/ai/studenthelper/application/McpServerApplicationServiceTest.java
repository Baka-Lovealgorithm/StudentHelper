package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.McpToolkitFactory;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.McpServerRequest;
import com.ai.studenthelper.common.dto.McpServerResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.McpServerRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpServerApplicationServiceTest {

    private final McpServerRepository repository = mock(McpServerRepository.class);
    private final McpToolkitFactory toolkitFactory = mock(McpToolkitFactory.class);
    private final S2FeatureService features = mock(S2FeatureService.class);
    private final McpServerApplicationService service =
            new McpServerApplicationService(repository, toolkitFactory, features);

    private static McpServerResponse server(long id, String transport, String url) {
        return new McpServerResponse(id, "demo", transport, url, true, null, null, 0,
                LocalDateTime.now());
    }

    @Test
    void onlyHttpUrlsAreAccepted() {
        doNothing().when(features).requireEnabled(S2Feature.MCP);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new McpServerRequest("demo", "STREAMABLE_HTTP", "file:///etc/passwd", false)));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
        assertTrue(error.getMessage().contains("http"));
        verify(repository, never()).insert(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void stdioTransportIsRejected() {
        doNothing().when(features).requireEnabled(S2Feature.MCP);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new McpServerRequest("demo", "STDIO", "http://127.0.0.1:8931/mcp", false)));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
        assertTrue(error.getMessage().contains("stdio"));
    }

    @Test
    void transportDefaultsToStreamableHttp() {
        doNothing().when(features).requireEnabled(S2Feature.MCP);
        when(repository.nameExists("demo", null)).thenReturn(false);
        when(repository.insert("demo", "STREAMABLE_HTTP", "https://example.com/mcp", false)).thenReturn(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(
                server(5L, "STREAMABLE_HTTP", "https://example.com/mcp")));
        McpServerResponse created = service.create(
                new McpServerRequest("demo", null, "https://example.com/mcp", false));
        assertEquals(5L, created.id());
        verify(repository).insert("demo", "STREAMABLE_HTTP", "https://example.com/mcp", false);
    }

    @Test
    void testRecordsFailureWithoutThrowing() {
        doNothing().when(features).requireEnabled(S2Feature.MCP);
        when(repository.findById(7L)).thenReturn(Optional.of(
                server(7L, "STREAMABLE_HTTP", "http://127.0.0.1:9/mcp")));
        when(toolkitFactory.discover(any())).thenThrow(new RuntimeException("Connection refused"));

        var result = service.test(7L);
        assertEquals("FAILED", result.status());
        assertTrue(result.tools().isEmpty());
        assertTrue(result.error().contains("Connection refused"));
        verify(repository).updateStatus(7L, "FAILED", "Connection refused", 0);
    }

    @Test
    void testRecordsDiscoveredTools() {
        doNothing().when(features).requireEnabled(S2Feature.MCP);
        when(repository.findById(8L)).thenReturn(Optional.of(
                server(8L, "SSE", "https://example.com/sse")));
        when(toolkitFactory.discover(any())).thenReturn(List.of("calc", "search"));

        var result = service.test(8L);
        assertEquals("OK", result.status());
        assertEquals(2, result.toolCount());
        assertEquals(List.of("calc", "search"), result.tools());
        verify(repository).updateStatus(8L, "OK", null, 2);
    }

    @Test
    void listRequiresToggle() {
        doThrow(new BusinessException(ErrorCode.E3004)).when(features).requireEnabled(S2Feature.MCP);
        BusinessException error = assertThrows(BusinessException.class, () -> service.list());
        assertEquals(ErrorCode.E3004, error.getErrorCode());
    }
}
