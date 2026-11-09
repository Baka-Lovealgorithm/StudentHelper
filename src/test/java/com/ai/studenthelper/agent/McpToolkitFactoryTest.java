package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.McpServerRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class McpToolkitFactoryTest {

    private final S2FeatureService features = mock(S2FeatureService.class);
    private final McpServerRepository repository = mock(McpServerRepository.class);
    private final McpToolkitFactory factory = new McpToolkitFactory(features, repository);

    @Test
    void closedToggleBuildsNoToolkitAndTouchesNoServer() {
        when(features.isEnabled(S2Feature.MCP)).thenReturn(false);
        assertNull(factory.open());
        verifyNoInteractions(repository);
    }

    @Test
    void enabledButNoServerBuildsNoToolkit() {
        when(features.isEnabled(S2Feature.MCP)).thenReturn(true);
        when(repository.findEnabled(3)).thenReturn(java.util.List.of());
        assertNull(factory.open());
    }

    @Test
    void closingNullToolkitIsNoOp() {
        factory.close(null);
    }
}
