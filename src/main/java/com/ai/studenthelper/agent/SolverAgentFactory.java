package com.ai.studenthelper.agent;

import com.ai.studenthelper.infra.model.ModelConfigResolver;
import io.agentscope.core.ReActAgent;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;

/**
 * 每次请求按当前配置现场构建 Model + ReActAgent，禁止单例与缓存（TD-12 / AC-105-2）。
 */
@Component
public class SolverAgentFactory {

    public ReActAgent create(String systemPrompt, ModelConfigResolver.ResolvedModelConfig config) {
        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(config.apiKey() == null || config.apiKey().isBlank() ? "ollama" : config.apiKey())
                .baseUrl(config.baseUrl())
                .modelName(config.modelName())
                .stream(true)
                .nativeStructuredOutput(false)
                .nativeStructuredOutputWithTools(false)
                .build();
        return ReActAgent.builder()
                .name("solver")
                .sysPrompt(systemPrompt)
                .model(model)
                .maxIters(1)
                .build();
    }

    /** S2 辅助调用（知识点抽取 / 相似题 / 判分）：非流式、独立实例、不复用解题 Agent。 */
    public ReActAgent createNonStreaming(String systemPrompt,
                                         ModelConfigResolver.ResolvedModelConfig config) {
        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(config.apiKey() == null || config.apiKey().isBlank() ? "ollama" : config.apiKey())
                .baseUrl(config.baseUrl())
                .modelName(config.modelName())
                .stream(false)
                .nativeStructuredOutput(false)
                .nativeStructuredOutputWithTools(false)
                .build();
        return ReActAgent.builder()
                .name("s2-helper")
                .sysPrompt(systemPrompt)
                .model(model)
                .maxIters(1)
                .build();
    }
}
