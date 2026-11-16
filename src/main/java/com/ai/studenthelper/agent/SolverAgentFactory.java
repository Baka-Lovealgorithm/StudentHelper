package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;

/**
 * 每次请求按当前配置现场构建 Model + ReActAgent，禁止单例与缓存（TD-12 / AC-105-2）。
 * T-301：MCP 工具通过可选 Toolkit 注入，生命周期由调用方在请求结束时关闭。
 * T-401：memory 开关开启时把记忆工具（memory_save / memory_search / memory_get）并入同一 Toolkit。
 */
@Component
public class SolverAgentFactory {

    private final McpToolkitFactory mcpToolkitFactory;
    private final MemoryTools memoryTools;
    private final S2FeatureService features;

    public SolverAgentFactory(McpToolkitFactory mcpToolkitFactory, MemoryTools memoryTools,
                              S2FeatureService features) {
        this.mcpToolkitFactory = mcpToolkitFactory;
        this.memoryTools = memoryTools;
        this.features = features;
    }

    public ReActAgent create(String systemPrompt, ModelConfigResolver.ResolvedModelConfig config) {
        return create(systemPrompt, config, null);
    }

    public ReActAgent create(String systemPrompt, ModelConfigResolver.ResolvedModelConfig config,
                             Toolkit toolkit) {
        return create(systemPrompt, config, toolkit, 3);
    }

    public ReActAgent create(String systemPrompt, ModelConfigResolver.ResolvedModelConfig config,
                             Toolkit toolkit, int maxItersWithTools) {
        return create(systemPrompt, config, toolkit, maxItersWithTools, null);
    }

    /**
     * CR-018：AUTO 学科自选会多一轮「调用 subject_get → 按结构生成」的迭代，
     * 单独把 maxIters 提高 1，不改变既有 MCP / 记忆链路的 3 轮上限。
     * CR-027：可选 Hook 用于采集工具 / 思考过程，null 时行为与旧链路逐字一致。
     */
    public ReActAgent create(String systemPrompt, ModelConfigResolver.ResolvedModelConfig config,
                             Toolkit toolkit, int maxItersWithTools, Hook processHook) {
        OpenAIChatModel model = buildModel(config, true);
        ReActAgent.Builder builder = ReActAgent.builder()
                .name("solver")
                .sysPrompt(systemPrompt)
                .model(model)
                .maxIters(toolkit == null ? 1 : maxItersWithTools);
        if (processHook != null) {
            builder.hook(processHook);
        }
        if (toolkit != null) {
            builder.toolkit(toolkit);
            // T-301 / AC-301-3：McpTool.checkPermissions 对非 read-only 的 MCP 工具一律返回 ASK，
            // 无人值守的解题链路没有确认入口，ASK 会让工具调用一直挂起（不执行、最终空文本 → E2005）。
            // 这里按工具名逐条加 ALLOW 规则（仅限本次装配的 MCP 工具），比整体 BYPASS 收得更紧。
            builder.permissionContext(allowAll(toolkit));
        }
        return builder.build();
    }

    /** 为本次装配的工具逐条放行；deny/ask 规则与其它工具不受影响。 */
    private static PermissionContextState allowAll(Toolkit toolkit) {
        PermissionContextState.Builder permissions = PermissionContextState.builder();
        for (String toolName : toolkit.getToolNames()) {
            permissions.addAllowRule(toolName,
                    new PermissionRule(toolName, null, PermissionBehavior.ALLOW, "managed-toolkit"));
        }
        return permissions.build();
    }

    /** S2 辅助调用（知识点抽取 / 相似题 / 判分）：非流式、独立实例、不复用解题 Agent。 */
    public ReActAgent createNonStreaming(String systemPrompt,
                                         ModelConfigResolver.ResolvedModelConfig config) {
        return ReActAgent.builder()
                .name("s2-helper")
                .sysPrompt(systemPrompt)
                .model(buildModel(config, false))
                .maxIters(1)
                .build();
    }

    /**
     * T-401：带工具的辅助调用（记忆写入 / 压缩摘要），需要多轮工具循环。
     * 与主链路一致，对本次装配的工具逐条放行，避免无人值守时 ASK 挂起。
     */
    public ReActAgent createNonStreaming(String systemPrompt,
                                         ModelConfigResolver.ResolvedModelConfig config,
                                         Toolkit toolkit) {
        ReActAgent.Builder builder = ReActAgent.builder()
                .name("memory-helper")
                .sysPrompt(systemPrompt)
                .model(buildModel(config, false))
                .maxIters(3);
        if (toolkit != null) {
            builder.toolkit(toolkit);
            builder.permissionContext(allowAll(toolkit));
        }
        return builder.build();
    }

    /** T-301：MCP 开关关闭或无可用 Server 时返回 null，调用方据此跳过工具装配。 */
    public Toolkit openToolkit() {
        Toolkit toolkit = mcpToolkitFactory.open();
        if (features.isEnabled(S2Feature.MEMORY)) {
            if (toolkit == null) {
                toolkit = new Toolkit();
            }
            // T-401 / AC-401-3：记忆工具只在开关开启时注册；关闭态完全不暴露
            toolkit.registerTool(memoryTools);
        }
        return toolkit;
    }

    public void closeToolkit(Toolkit toolkit) {
        mcpToolkitFactory.close(toolkit);
    }

    private static OpenAIChatModel buildModel(ModelConfigResolver.ResolvedModelConfig config,
                                              boolean stream) {
        return OpenAIChatModel.builder()
                .apiKey(config.apiKey() == null || config.apiKey().isBlank() ? "ollama" : config.apiKey())
                .baseUrl(config.baseUrl())
                .modelName(config.modelName())
                .stream(stream)
                .nativeStructuredOutput(false)
                .nativeStructuredOutputWithTools(false)
                .build();
    }
}
