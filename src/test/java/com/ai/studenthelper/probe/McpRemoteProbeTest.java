package com.ai.studenthelper.probe;

import com.ai.studenthelper.agent.McpToolkitFactory;
import com.ai.studenthelper.agent.SolverAgentFactory;
import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 探针：本地起一个真实 MCP Server（Streamable HTTP，/mcp），验证
 * ① 官方文档的 `buildAsync().block()` 与项目原先的 `buildSync()` 两种注册方式；
 * ② 产品链路 McpToolkitFactory.open() + SolverAgentFactory 能否真正执行 MCP 工具并采用结果。
 * 默认不参与回归，需 PROBE_MODEL=true。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"studenthelper.startup.db-check=false"})
@EnabledIfEnvironmentVariable(named = "PROBE_MODEL", matches = "true")
class McpRemoteProbeTest {

    private static final String SYS_PROMPT =
            "你必须使用工具。需要随机数时调用 roll_dice，禁止编造数字。";

    private static final String USER_PROMPT =
            "请调用 roll_dice 取一个随机数，然后只把这个数字原样回答。";

    private static final AtomicInteger MCP_CALLED = new AtomicInteger();

    @LocalServerPort
    private int port;

    @Autowired
    private ModelConfigResolver resolver;

    @Autowired
    private SolverAgentFactory solverAgentFactory;

    @Autowired
    private McpToolkitFactory mcpToolkitFactory;

    @Autowired
    private S2FeatureService features;

    @Autowired
    private JdbcTemplate jdbc;

    @TestConfiguration
    static class McpServerConfig {

        static McpSyncServer server;

        @Bean
        ServletRegistrationBean<HttpServlet> probeMcpServlet() {
            HttpServletStreamableServerTransportProvider provider =
                    HttpServletStreamableServerTransportProvider.builder()
                            .mcpEndpoint("/mcp")
                            .build();

            McpSchema.Tool tool = McpSchema.Tool.builder()
                    .name("roll_dice")
                    .description("掷一个 1 到 100 的随机整数并返回。需要随机数时必须调用。")
                    .inputSchema(new McpSchema.JsonSchema(
                            "object", Map.of(), List.of(), false, Map.of(), Map.of()))
                    .build();

            McpServerFeatures.SyncToolSpecification spec =
                    McpServerFeatures.SyncToolSpecification.builder()
                            .tool(tool)
                            .callHandler((exchange, request) -> {
                                int value = 1 + ThreadLocalRandom.current().nextInt(100);
                                MCP_CALLED.incrementAndGet();
                                System.out.println("[MCP-PROBE][SERVER] roll_dice 被调用，返回 " + value);
                                return McpSchema.CallToolResult.builder()
                                        .addTextContent(String.valueOf(value))
                                        .build();
                            })
                            .build();

            server = McpServer.sync(provider)
                    .serverInfo("probe-mcp", "1.0.0")
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                    .tools(spec)
                    .build();

            return new ServletRegistrationBean<>(provider, "/mcp");
        }
    }

    private String url() {
        return "http://127.0.0.1:" + port + "/mcp";
    }

    /** 对比两种注册方式，均走产品侧 SolverAgentFactory（含 MCP 工具放行规则）。 */
    @Test
    void compareSyncAndAsyncRegistration() {
        System.out.println("[MCP-PROBE] server url=" + url());
        runToolkit("SYNC(buildSync)", registerToolkit(buildSync(url())));
        runToolkit("ASYNC(buildAsync().block())", registerToolkit(buildAsync(url())));
    }

    /** 产品链路：DB 配置 + 开关 → McpToolkitFactory.open() → SolverAgentFactory → 真实调用。 */
    @Test
    void productPathExecutesMcpTool() {
        MCP_CALLED.set(0);
        jdbc.update("DELETE FROM t_mcp_server WHERE name = ?", "probe-e2e");
        jdbc.update("INSERT INTO t_mcp_server(name, transport, url, enabled, tool_count, "
                        + "created_at, updated_at) VALUES (?, 'STREAMABLE_HTTP', ?, 1, 1, NOW(3), NOW(3))",
                "probe-e2e", url());
        features.update("mcp", true);

        Toolkit toolkit = null;
        try {
            toolkit = mcpToolkitFactory.open();
            assertNotNull(toolkit, "McpToolkitFactory.open() 返回 null");
            System.out.println("[MCP-PROBE][PRODUCT] tools=" + toolkit.getToolNames());
            runToolkit("PRODUCT(open+SolverAgentFactory)", toolkit);
        } finally {
            mcpToolkitFactory.close(toolkit);
            features.update("mcp", false);
            jdbc.update("DELETE FROM t_mcp_server WHERE name = ?", "probe-e2e");
        }
    }

    private Toolkit registerToolkit(McpClientWrapper wrapper) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerMcpClient(wrapper).block(Duration.ofSeconds(15));
        return toolkit;
    }

    private void runToolkit(String label, Toolkit toolkit) {
        MCP_CALLED.set(0);
        AtomicReference<String> closed = new AtomicReference<>("");
        try {
            System.out.println("[MCP-PROBE][" + label + "] tools=" + toolkit.getToolNames());
            System.out.println("[MCP-PROBE][" + label + "] schemas="
                    + toolkit.getToolSchemas().stream()
                            .map(s -> s.getName() + s.getParameters()).toList());

            ModelConfigResolver.ResolvedModelConfig config = resolver.resolveCurrent();
            ReActAgent agent = solverAgentFactory.create(SYS_PROMPT, config, toolkit);

            AtomicReference<Msg> finalMsg = new AtomicReference<>();
            AtomicReference<Throwable> error = new AtomicReference<>();
            agent.stream(List.of(new UserMessage(USER_PROMPT)),
                            StreamOptions.builder().eventTypes(EventType.ALL).incremental(false).build())
                    .doOnNext(e -> {
                        Msg msg = e.getMessage();
                        if (e.getType() == EventType.AGENT_RESULT) {
                            finalMsg.set(msg);
                        }
                        System.out.println("[MCP-PROBE][" + label + "][EVENT] " + e.getType()
                                + " text=" + (msg == null ? null : msg.getTextContent())
                                + " blocks=" + (msg == null ? null : msg.getContent()));
                    })
                    .doOnError(error::set)
                    .blockLast(Duration.ofSeconds(90));

            if (error.get() != null) {
                System.out.println("[MCP-PROBE][" + label + "] FAILED " + error.get());
                throw new IllegalStateException(label + " 失败", error.get());
            }
            assertNotNull(finalMsg.get(), label + " 无 AGENT_RESULT");
            closed.set(finalMsg.get().getTextContent() == null ? "" : finalMsg.get().getTextContent());
            System.out.println("[MCP-PROBE][" + label + "] final=" + closed.get()
                    + " mcpCalled=" + MCP_CALLED.get());
            assertEquals(1, MCP_CALLED.get(), label + " 未触发 MCP 工具调用");
        } finally {
            try {
                toolkit.closeMcpClients();
            } catch (Exception ignored) {
                // 探针清理失败不影响结论
            }
        }
    }

    private static McpClientWrapper buildSync(String url) {
        return McpClientBuilder.create("probe-sync")
                .streamableHttpTransport(url)
                .timeout(Duration.ofSeconds(20))
                .initializationTimeout(Duration.ofSeconds(10))
                .buildSync();
    }

    private static McpClientWrapper buildAsync(String url) {
        return McpClientBuilder.create("probe-async")
                .streamableHttpTransport(url)
                .timeout(Duration.ofSeconds(20))
                .initializationTimeout(Duration.ofSeconds(10))
                .buildAsync()
                .block(Duration.ofSeconds(15));
    }
}
