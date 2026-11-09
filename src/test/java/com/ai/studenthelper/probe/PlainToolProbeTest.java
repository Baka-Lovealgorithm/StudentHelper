package com.ai.studenthelper.probe;

import com.ai.studenthelper.infra.model.ModelConfigResolver;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 探针：验证 AgentScope 2.0.4 能否驱动「普通 @Tool 工具」完成一轮工具调用。
 * 与 MCP 无关，用来把「框架/模型不支持工具」与「MCP 装配有问题」区分开。
 * 不纳入常规回归（依赖真实模型与网络），仅在排查时手动运行。
 */
@SpringBootTest(properties = {"studenthelper.startup.db-check=false"})
@EnabledIfEnvironmentVariable(named = "PROBE_MODEL", matches = "true",
        disabledReason = "手动探针：需要真实模型与网络，默认不参与回归")
class PlainToolProbeTest {

    private static final String SYS_PROMPT =
            "你是一个必须使用工具的助手。凡是需要随机数、时间或计算结果，一律调用工具获取，禁止编造。";

    private static final String USER_PROMPT =
            "请调用 roll_dice 工具取一个随机数，然后只把这个数字原样回答给我。";

    @Autowired
    private ModelConfigResolver resolver;

    private static final java.util.concurrent.atomic.AtomicInteger INVOKE_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();

    /** 普通工具：方法注解注册，不经过 MCP。返回值模型无法凭空知道。 */
    public static class CalcTools {
        @Tool(name = "roll_dice", description = "掷一个 1 到 100 的随机整数并返回。需要随机数时必须调用此工具。")
        public String rollDice() {
            int value = 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(100);
            INVOKE_COUNT.incrementAndGet();
            System.out.println("[PROBE][TOOL] roll_dice 被调用，返回 " + value);
            return String.valueOf(value);
        }
    }

    @Test
    void plainTool_nonStreamingCall() {
        INVOKE_COUNT.set(0);
        ModelConfigResolver.ResolvedModelConfig config = resolver.resolveCurrent();
        System.out.println("[PROBE] model=" + config.modelName() + " baseUrl=" + config.baseUrl());

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new CalcTools());
        toolkit.getToolSchemas().forEach(s ->
                System.out.println("[PROBE] schema name=" + s.getName()
                        + " params=" + s.getParameters()));

        ReActAgent agent = ReActAgent.builder()
                .name("probe-call")
                .sysPrompt(SYS_PROMPT)
                .model(buildModel(config, false))
                .toolkit(toolkit)
                .maxIters(3)
                .build();

        Msg result = agent.call(List.of(new UserMessage(USER_PROMPT)))
                .block(Duration.ofSeconds(120));

        assertNotNull(result, "非流式 call 返回 null");
        System.out.println("[PROBE][CALL] text=" + result.getTextContent());
        System.out.println("[PROBE][CALL] blocks=" + result.getContent());
        System.out.println("[PROBE][CALL] usage=" + result.getChatUsage());
        System.out.println("[PROBE][CALL] toolInvoked=" + INVOKE_COUNT.get());
    }

    @Test
    void plainTool_streaming() {
        INVOKE_COUNT.set(0);
        ModelConfigResolver.ResolvedModelConfig config = resolver.resolveCurrent();

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new CalcTools());

        ReActAgent agent = ReActAgent.builder()
                .name("probe-stream")
                .sysPrompt(SYS_PROMPT)
                .model(buildModel(config, true))
                .toolkit(toolkit)
                .maxIters(3)
                .build();

        StreamOptions options = StreamOptions.builder()
                .eventTypes(EventType.ALL)
                .incremental(false)
                .includeReasoningChunk(true)
                .includeSummaryChunk(true)
                .build();

        AtomicReference<Msg> finalMsg = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        agent.stream(List.of(new UserMessage(USER_PROMPT)), options)
                .doOnNext(e -> {
                    Msg msg = e.getMessage();
                    if (e.getType() == EventType.AGENT_RESULT) {
                        finalMsg.set(msg);
                    }
                    System.out.println("[PROBE][EVENT] type=" + e.getType()
                            + " text=" + (msg == null ? null : msg.getTextContent())
                            + " blocks=" + (msg == null ? null : msg.getContent()));
                })
                .doOnError(error::set)
                .blockLast(Duration.ofSeconds(120));

        if (error.get() != null) {
            System.out.println("[PROBE][STREAM] ERROR " + error.get());
            throw new IllegalStateException(error.get());
        }
        Msg result = finalMsg.get();
        assertNotNull(result, "流式未收到 AGENT_RESULT");
        System.out.println("[PROBE][STREAM] final=" + result.getTextContent());
        System.out.println("[PROBE][STREAM] usage=" + result.getChatUsage());
        System.out.println("[PROBE][STREAM] toolInvoked=" + INVOKE_COUNT.get());
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
