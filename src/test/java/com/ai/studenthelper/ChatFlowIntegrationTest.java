package com.ai.studenthelper;

import com.ai.studenthelper.application.ChatApplicationService;
import com.ai.studenthelper.application.SessionApplicationService;
import com.ai.studenthelper.application.SolutionStreamSink;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.ChatCommand;
import com.ai.studenthelper.common.dto.DonePayload;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.ModelConfig;
import com.ai.studenthelper.domain.enums.AgentStage;
import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.ModelConfigRepository;
import com.ai.studenthelper.infra.persistence.ModelProviderRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全链路集成测试：提交 → AgentScope 流式 → 六段落库 → done，以及 E2005 原始输出归档。
 *
 * 模型侧用 JDK 内置 HttpServer 起 OpenAI 兼容 SSE 桩，不触碰任何真实付费端点；
 * 数据库走真实 MySQL，但整个测试方法在事务内执行并回滚，测试数据不残留。
 * 需要 DB_PASSWORD 环境变量（只经环境变量传递，不写入仓库）；未设置时本类整体跳过，
 * 保证日常 `mvn test` 仍然全绿且不依赖数据库。
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class ChatFlowIntegrationTest {

    private static final String FULL_ANSWER = """
            ## ① 题目理解
            这是一道用于集成测试的数据结构选择题，考察栈与队列的基本性质。
            ## ② 涉及知识点
            栈的后进先出、队列的先进先出、循环队列。
            ## ③ 解题思路
            逐项判断四个选项，排除与定义矛盾的叙述。
            ## ④ 详细步骤
            第一步，写出栈只能在栈顶插入删除，因此为后进先出。
            第二步，写出队列在队尾入队、队头出队，因此为先进先出；据此判断选项。
            ## ⑤ 最终答案
            答案为 C。
            ## ⑥ 易错点 / 验算
            注意不要把两者的进出顺序写反；用 n=1 与 n=8 各验算一次均成立。
            """;

    private static final String MALFORMED_ANSWER = """
            ## ① 题目理解
            只有前三段，缺少后续段落。
            ## ② 涉及知识点
            栈与队列。
            ## ③ 解题思路
            逐项判断。
            """;

    private static final AtomicReference<String> STUB_BODY = new AtomicReference<>(FULL_ANSWER);
    private static HttpServer stubServer;

    @Autowired private ChatApplicationService chatService;
    @Autowired private SessionApplicationService sessionService;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ModelConfigRepository modelConfigRepository;
    @Autowired private ModelProviderRepository modelProviderRepository;
    @Autowired private ApiKeyCipher apiKeyCipher;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void startStub() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // catch-all：无论 SDK 把 baseUrl 拼成什么路径都能命中
        stubServer.createContext("/", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                byte[] payload = buildSse(STUB_BODY.get()).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, payload.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(payload);
                }
            } catch (IOException e) {
                exchange.close();
            }
        });
        stubServer.start();
    }

    @AfterAll
    static void stopStub() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
    }

    @Test
    void submitStreamsAndPersistsBothMessagesWithHint() {
        STUB_BODY.set(FULL_ANSWER);
        installStubConfig();
        Long sessionId = sessionService.create("IT-full-flow").id();

        RecordingSink sink = new RecordingSink();
        chatService.solve(new ChatCommand(sessionId, "题目：判断栈与队列的性质", "用初学者能懂的话", 0, List.of()), sink);

        assertEquals("status:UNDERSTANDING", sink.events.get(0));
        assertEquals("status:GENERATING", sink.events.get(1));
        assertEquals("done", sink.events.get(sink.events.size() - 1), "失败时不应发送 done");
        assertNull(sink.error);
        assertNotNull(sink.done);
        assertTrue(sink.text.toString().contains("## ⑤ 最终答案"));

        List<Message> messages = messageRepository.findBySessionId(sessionId);
        assertEquals(2, messages.size(), "一轮成功解题应落 USER + ASSISTANT 两条消息");
        Message user = messages.get(0);
        assertEquals("USER", user.getRole());
        assertEquals("题目：判断栈与队列的性质", user.getContent());
        assertEquals("用初学者能懂的话", user.getHint(), "AC-109-3 提示词原文落库");
        assertEquals("SUCCESS", user.getStatus());

        Message assistant = messages.get(1);
        assertEquals("ASSISTANT", assistant.getRole());
        assertEquals("SUCCESS", assistant.getStatus());
        assertTrue(assistant.getContent().contains("## ⑤ 最终答案"));
        assertTrue(assistant.getContent().contains("本内容由 AI 生成，请自行核对"));

        Integer logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_call_log WHERE message_id = ? AND status = 'SUCCESS'",
                Integer.class, assistant.getId());
        assertEquals(1, logs, "成功调用日志应落库");
    }

    @Test
    void malformedAnswerReturnsE2005AndArchivesRawOutput() {
        STUB_BODY.set(MALFORMED_ANSWER);
        installStubConfig();
        Long sessionId = sessionService.create("IT-e2005").id();

        RecordingSink sink = new RecordingSink();
        chatService.solve(new ChatCommand(sessionId, "题目：六段缺失", null, 0, List.of()), sink);

        assertEquals(ErrorCode.E2005, sink.error);
        assertNull(sink.done, "失败只发 error，不发 done");

        List<Message> messages = messageRepository.findBySessionId(sessionId);
        assertTrue(messages.stream().anyMatch(m -> "USER".equals(m.getRole())
                && "FAILED".equals(m.getStatus()) && "E2005".equals(m.getErrorCode())));
        assertTrue(messages.stream().anyMatch(m -> "ASSISTANT".equals(m.getRole())
                        && "FAILED".equals(m.getStatus()) && "E2005".equals(m.getErrorCode())
                        && m.getContent() != null && m.getContent().contains("① 题目理解")),
                "D-01：格式异常时模型原始输出必须归档落库");
    }

    private void installStubConfig() {
        modelConfigRepository.clearCurrent();
        LocalDateTime now = LocalDateTime.now();
        ModelConfig config = new ModelConfig();
        config.setName("IT-stub");
        config.setProvider("custom");
        config.setBaseUrl("http://127.0.0.1:" + stubServer.getAddress().getPort() + "/v1");
        config.setApiKeyCipher(apiKeyCipher.encrypt("stub-key"));
        config.setModelName("stub-model");
        config.setVisionSupported(0);
        config.setStreamSupported(1);
        config.setIsCurrent(1);
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        modelConfigRepository.save(config);

        // CR-012 起主链路按「会话模型 → 全局默认」解析新表。测试方法整体在事务内并回滚，
        // 这里把桩模型设为临时默认，create() 就会让新会话指向桩，绝不触碰真实付费端点。
        modelProviderRepository.clearDefault();
        Long providerId = modelProviderRepository.insertProvider("IT-stub-provider",
                config.getBaseUrl(), config.getApiKeyCipher());
        Long modelId = modelProviderRepository.insertModel(providerId, "stub-model",
                false, true, null, false);
        modelProviderRepository.setDefault(modelId);
    }

    private static String buildSse(String answer) {
        StringBuilder sb = new StringBuilder();
        sb.append("data: {\"id\":\"chatcmpl-it\",\"object\":\"chat.completion.chunk\",\"created\":1700000000,")
                .append("\"model\":\"stub-model\",\"choices\":[{\"index\":0,")
                .append("\"delta\":{\"role\":\"assistant\",\"content\":\"\"},\"finish_reason\":null}]}\n\n");
        for (int i = 0; i < answer.length(); i += 60) {
            String part = answer.substring(i, Math.min(answer.length(), i + 60));
            sb.append("data: {\"id\":\"chatcmpl-it\",\"object\":\"chat.completion.chunk\",\"created\":1700000000,")
                    .append("\"model\":\"stub-model\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"")
                    .append(jsonEscape(part)).append("\"},\"finish_reason\":null}]}\n\n");
        }
        sb.append("data: {\"id\":\"chatcmpl-it\",\"object\":\"chat.completion.chunk\",\"created\":1700000000,")
                .append("\"model\":\"stub-model\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],")
                .append("\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":200,\"total_tokens\":300}}\n\n");
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class RecordingSink implements SolutionStreamSink {
        private final List<String> events = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private DonePayload done;
        private ErrorCode error;

        @Override public void onStatus(AgentStage stage) { events.add("status:" + stage.name()); }
        @Override public void onDelta(String chunk, int index) { events.add("delta"); text.append(chunk); }
        @Override public void onDone(DonePayload payload) { events.add("done"); done = payload; }
        @Override public void onError(ErrorCode code, String detail) { events.add("error:" + code.getCode()); error = code; }
    }
}
