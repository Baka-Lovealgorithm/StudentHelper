package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.common.dto.McpServerResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.McpServerRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * T-301 远程 MCP 工具装配（DD-10）：仅在开关开启且有启用 Server 时才构建 Toolkit。
 * 任何连接失败都只影响工具可用性，绝不影响解题主链路；请求结束必须 close()。
 */
@Component
public class McpToolkitFactory {

    private static final Logger log = LoggerFactory.getLogger(McpToolkitFactory.class);
    private static final int MAX_SERVERS = 3;
    private static final int MAX_TOOLS = 32;
    private static final int CONNECT_ATTEMPTS = 2;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DISCOVER_TIMEOUT = Duration.ofSeconds(10);

    private final S2FeatureService features;
    private final McpServerRepository repository;

    public McpToolkitFactory(S2FeatureService features, McpServerRepository repository) {
        this.features = features;
        this.repository = repository;
    }

    /** 返回可用的 Toolkit；关闭态、无启用 Server 或全部连接失败时返回 null。 */
    public Toolkit open() {
        if (!features.isEnabled(S2Feature.MCP)) {
            return null;
        }
        List<McpServerResponse> servers = repository.findEnabled(MAX_SERVERS);
        if (servers.isEmpty()) {
            return null;
        }
        Toolkit toolkit = new Toolkit();
        boolean anyRegistered = false;
        for (McpServerResponse server : servers) {
            for (int attempt = 1; attempt <= CONNECT_ATTEMPTS && !anyRegistered; attempt++) {
                McpClientWrapper client = null;
                try {
                    client = buildClient(server);
                    toolkit.registerMcpClient(client).block(DISCOVER_TIMEOUT);
                    anyRegistered = true;
                    log.info("MCP_CONNECTED server={} transport={} attempt={}",
                            server.name(), server.transport(), attempt);
                } catch (Exception e) {
                    closeQuietly(client);
                    log.warn("MCP_CONNECT_FAILED server={} attempt={} reason={}",
                            server.name(), attempt, reason(e));
                }
            }
        }
        if (!anyRegistered) {
            closeQuietly(toolkit);
            return null;
        }
        int toolCount = toolkit.getToolNames().size();
        if (toolCount > MAX_TOOLS) {
            log.warn("MCP_TOO_MANY_TOOLS tools={} limit={}", toolCount, MAX_TOOLS);
            closeQuietly(toolkit);
            return null;
        }
        log.info("MCP_TOOLKIT_READY tools={}", toolCount);
        return toolkit;
    }

    public void close(Toolkit toolkit) {
        closeQuietly(toolkit);
    }

    /** 供「测试连接」接口使用：连一次、列工具、立即关闭。 */
    public List<String> discover(McpServerResponse server) {
        McpClientWrapper client = null;
        try {
            client = buildClient(server);
            client.initialize().block(DISCOVER_TIMEOUT);
            List<McpSchema.Tool> tools = client.listTools().block(DISCOVER_TIMEOUT);
            List<String> names = new ArrayList<>();
            if (tools != null) {
                for (McpSchema.Tool tool : tools) {
                    names.add(tool.name());
                }
            }
            return names;
        } finally {
            closeQuietly(client);
        }
    }

    private static McpClientWrapper buildClient(McpServerResponse server) {
        // MCP SDK 通过 ServiceLoader 查 JSON 映射器；在 Reactor 工作线程上 TCCL 可能不是应用类加载器，
        // 会抛「No default McpJsonMapper implementation found」，这里显式指定后再恢复
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(McpToolkitFactory.class.getClassLoader());
        try {
            McpClientBuilder builder = McpClientBuilder.create(server.name())
                    .timeout(CALL_TIMEOUT)
                    .initializationTimeout(CONNECT_TIMEOUT);
            if ("SSE".equalsIgnoreCase(server.transport())) {
                builder.sseTransport(server.url());
            } else {
                builder.streamableHttpTransport(server.url());
            }
            // 与官方文档一致：buildAsync().block()，避免在 Reactor 线程上用同步客户端
            return builder.buildAsync().block(CONNECT_TIMEOUT);
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static void closeQuietly(McpClientWrapper client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.debug("MCP_CLOSE_IGNORED reason={}", reason(e));
        }
    }

    private static void closeQuietly(Toolkit toolkit) {
        if (toolkit == null) {
            return;
        }
        try {
            toolkit.closeMcpClients();
        } catch (Exception e) {
            log.debug("MCP_TOOLKIT_CLOSE_IGNORED reason={}", reason(e));
        }
    }

    private static String reason(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
