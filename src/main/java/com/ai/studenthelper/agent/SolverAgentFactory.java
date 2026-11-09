package com.ai.studenthelper.agent;

import com.ai.studenthelper.infra.model.ModelConfigResolver;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;

/**
 * 每次请求按当前配置现场构建 Model + ReActAgent，禁止单例与缓存（TD-12 / AC-105-2）。
 * T-301：MCP 工具通过可选 Toolkit 注入，生命周期由调用方在请求结束时关闭。
 */
@Component
public class SolverAgentFactory {

    private final McpToolkitFactory mcpToolkitFactory;

    public SolverAgentFactory(McpToolkitFactory mcpToolkitFactory) {
        this.mcpToolkitFactory = mcpToolkitFactory;
    }

    public ReActAgent create(String systemPrompt, ModelConfigResolver.ResolvedModelConfig config) {
        return create(systemPrompt, config, null);
    }

    public ReActAgent create(String systemPrompt, ModelConfigResolver.ResolvedModelConfig config,
                             Toolkit toolkit) {
        OpenAIChatModel model = buildModel(config, true);
        ReActAgent.Builder builder = ReActAgent.builder()
                .name("solver")
                .sysPrompt(systemPrompt)
                .model(model)
                .maxIters(toolkit == null ? 1 : 3);
        if (toolkit != null) {
            builder.toolkit(toolkit);
        }
        return builder.build();
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

    /** T-301：开关关闭或无可用 Server 时返回 null，调用方据此跳过工具装配。 */
    public Toolkit openToolkit() {
        return mcpToolkitFactory.open();
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
