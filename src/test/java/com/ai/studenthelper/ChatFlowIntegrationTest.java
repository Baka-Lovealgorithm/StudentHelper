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
import org.junit.jupiter.api.BeforeEach;
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
@org.springframework.test.context.TestPropertySource(
        properties = "studenthelper.memory.dir=./target/test-memory")
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

    private static final String EXPLAIN_ANSWER = """
            ## ① 概念定义
            后进先出（LIFO, Last In First Out）指最后进入的元素最先被取出。
            ## ② 关键性质与适用条件
            只允许在栈顶插入与删除，因此顺序被严格反转；适用于括号匹配、函数调用栈等场景。
            ## ③ 典型例子
            依次压入 1、2、3，弹出顺序为 3、2、1。
            ## ④ 与相邻概念的区别
            队列是先进先出，插入在队尾、删除在队头，两者顺序相反。
            ## ⑤ 常见误区
            把栈的「顶」误认为最先插入的位置，导致顺序判断颠倒。
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

    /** CR-039：意图分类桩。分类请求不覆盖 STUB_REQUEST，避免干扰主请求断言。 */
    private static final AtomicReference<String> STUB_INTENT = new AtomicReference<>("SOLVE");

    /** CR-033：首轮回答首行带【会话标题】标记行，验证标题写回与正文剥离。 */
    private static final String TITLED_ANSWER = "【会话标题】栈与队列性质辨析\n"
            + FULL_ANSWER;
    /** TCR-038 / AC-201-5：记录桩收到的请求体，用于核对实际发给模型的 system prompt。 */
    private static final AtomicReference<String> STUB_REQUEST = new AtomicReference<>("");
    private static HttpServer stubServer;

    /** CR-039：每个用例前复位分类桩，避免静态状态在方法之间泄漏。 */
    @BeforeEach
    void resetStubIntent() {
        STUB_INTENT.set("SOLVE");
    }

    @Autowired private ChatApplicationService chatService;
    @Autowired private SessionApplicationService sessionService;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ModelConfigRepository modelConfigRepository;
    @Autowired private ModelProviderRepository modelProviderRepository;
    @Autowired private ApiKeyCipher apiKeyCipher;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private com.ai.studenthelper.infra.memory.MemoryFileStore memoryFileStore;

    @BeforeAll
    static void startStub() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // catch-all：无论 SDK 把 baseUrl 拼成什么路径都能命中
        stubServer.createContext("/", exchange -> {
            try {
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                String bodyText = new String(requestBody, StandardCharsets.UTF_8);
                // CR-039：回答形态判别走非流式调用，返回最小 JSON；不记录为主请求
                if (bodyText.contains("回答形态判别器")) {
                    byte[] json = stubIntentJson(STUB_INTENT.get()).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, json.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(json);
                    }
                    return;
                }
                STUB_REQUEST.set(bodyText);
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

    /** CR-033（AC-104-12~13）：首轮同轮返回标题 → 写回会话、正文剥离；手改标题不被第二轮覆盖。 */
    @Test
    void firstRoundAutoTitleWritesSessionTitleAndStripsMarker() {
        STUB_BODY.set(TITLED_ANSWER);
        installStubConfig();
        Long sessionId = sessionService.create(null).id();
        assertEquals("新会话", sessionService.detail(sessionId).session().title());

        RecordingSink sink = new RecordingSink();
        chatService.solve(new ChatCommand(sessionId, "题目：判断栈与队列的性质", null, 0, List.of()), sink);

        assertEquals("done", sink.events.get(sink.events.size() - 1));
        // 标题随首轮写回；落库正文剥离标记行，六段与免责声明不变
        assertEquals("栈与队列性质辨析", sessionService.detail(sessionId).session().title());
        Message assistant = messageRepository.findBySessionId(sessionId).get(1);
        assertFalse(assistant.getContent().contains("【会话标题】"));
        assertTrue(assistant.getContent().startsWith("## ① 题目理解"), "剥离后正文直接以六段开头");
        assertTrue(assistant.getContent().contains("本内容由 AI 生成，请自行核对"));

        // 手动重命名后第二轮：标题不被覆盖，且请求不再包含首轮标题指令
        sessionService.rename(sessionId, "手改标题");
        chatService.solve(new ChatCommand(sessionId, "题目：再问一题", null, 0, List.of()), sink);
        assertEquals("手改标题", sessionService.detail(sessionId).session().title(),
                "手动重命名过的标题永不被模型覆盖");
        assertFalse(STUB_REQUEST.get().contains("这是本会话的第一轮解答"),
                "多轮不应追加首轮标题指令");
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

    // TCR-039 / AC-401-2：MEMORY.md 必须出现在「实际发给模型」的 system prompt 中，关闭态须消失。
    @Test
    void memoryFileReachesModelPromptAndVanishesWhenFeatureIsOff() throws Exception {
        STUB_BODY.set(FULL_ANSWER);
        installStubConfig();
        // 文件写入落在 target/test-memory，不触碰真实 data/memory
        memoryFileStore.updateUserSection("## 学习偏好\n- 讲通俗些，少用公式", null);
        // 开关改动在测试事务内，方法结束自动回滚
        jdbcTemplate.update("UPDATE t_feature_toggle SET enabled = 1 WHERE feature_key = 'memory'");

        Long onSession = sessionService.create("IT-memory-on").id();
        RecordingSink onSink = new RecordingSink();
        chatService.solve(new ChatCommand(onSession, "题目：判断栈与队列的性质", null, 0, List.of()), onSink);
        assertNull(onSink.error);
        assertNotNull(onSink.done);
        String onPrompt = readCapturedRequest();
        assertTrue(onPrompt.contains("【长期记忆】"), "实际发给模型的 prompt 应含长期记忆段");
        assertTrue(onPrompt.contains("讲通俗些，少用公式"), "MEMORY.md 内容应进入 prompt");

        jdbcTemplate.update("UPDATE t_feature_toggle SET enabled = 0 WHERE feature_key = 'memory'");
        STUB_REQUEST.set("");
        Long offSession = sessionService.create("IT-memory-off").id();
        RecordingSink offSink = new RecordingSink();
        chatService.solve(new ChatCommand(offSession, "题目：判断栈与队列的性质", null, 0, List.of()), offSink);
        assertNull(offSink.error);
        assertNotNull(offSink.done);
        String offPrompt = readCapturedRequest();
        assertFalse(offPrompt.contains("【长期记忆】"), "关闭态不应出现长期记忆段");
        assertFalse(offPrompt.contains("讲通俗些，少用公式"), "关闭态不应读取记忆文件");
    }

    // ------------------------------------------------------------------
    // TCR-041 / CR-018：学科规范三态、可插拔结构、追问自由问答的记录桩验证
    // ------------------------------------------------------------------

    private static final String MATH_ANSWER = """
            ## ① 题意与已知条件
            求 x→0 时 sin x / x 的极限，x 为实数。
            ## ② 定理与公式
            重要极限 lim(x→0) sin x / x = 1，适用条件为 x 从两侧趋近 0。
            ## ③ 推导过程
            第一步，把原式写成 (sin x)/x；第二步，代入重要极限并验证两侧极限一致。
            ## ④ 结论与验证
            极限为 1；取 x=0.01 与 x=-0.01 回代，数值均接近 1。
            ## ⑤ 易错点与适用边界
            角度必须用弧度；x 只趋近 0 时成立，不能直接推广到 x→∞。
            """;

    /** AC-501-4 / 7 / 11：MANUAL 直接注入规范全文、按 5 段解析、schema 与标签落库。 */
    @Test
    void subjectSkillManualInjectsSpecAndPersistsMathSchema() throws Exception {
        STUB_BODY.set(MATH_ANSWER);
        installStubConfig();
        Long skillId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_subject_skill WHERE code = 'ADVANCED_MATH'", Long.class);
        assertNotNull(skillId, "upgrade-s6.sql 应已写入内置高数学科");
        Long sessionId = sessionService.create("IT-subject-manual").id();
        sessionService.setSubject(sessionId, "MANUAL", skillId);

        RecordingSink sink = new RecordingSink();
        chatService.solve(new ChatCommand(sessionId,
                "求 lim(x→0) sin x / x 的极限", null, 0, List.of()), sink);
        assertNull(sink.error);
        assertNotNull(sink.done);

        String prompt = readCapturedRequest();
        assertTrue(prompt.contains("【回答规范：高等数学 · 解题】"), "MANUAL 解题规范应进入模型请求");
        assertTrue(prompt.contains("必须写清上下限与趋近方向"), "seed 规范正文应进入模型请求");
        assertTrue(prompt.contains("## ④ 结论与验证"), "应按高数五段结构声明输出");
        assertFalse(prompt.contains("subject_get"), "MANUAL 不注册 / 调用学科工具");

        List<Message> messages = messageRepository.findBySessionId(sessionId);
        assertEquals(2, messages.size());
        Message assistant = messages.get(1);
        assertNotNull(assistant.getSectionSchema());
        assertTrue(assistant.getSectionSchema().contains("## ⑤ 易错点与适用边界"));
        assertTrue(assistant.getSubjectLabel().contains("高等数学"));
        String hit = jdbcTemplate.queryForObject(
                "SELECT subject_hit FROM t_call_log WHERE message_id = ? AND status = 'SUCCESS'",
                String.class, assistant.getId());
        assertTrue(hit != null && hit.contains("ADVANCED_MATH"), "命中理由应写入 t_call_log");
    }

    /** AC-501-4 / 10 + CR-039：AUTO 清单与工具定义进入模型请求；会话 OFF 后逐字消失。 */
    @Test
    void subjectSkillAutoCatalogReachesModelAndOffStateIsVerbatim() throws Exception {
        STUB_BODY.set(FULL_ANSWER);
        installStubConfig();
        Long autoSession = sessionService.create("IT-subject-auto").id();
        RecordingSink autoSink = new RecordingSink();
        chatService.solve(new ChatCommand(autoSession,
                "题目：判断栈与队列的性质", null, 0, List.of()), autoSink);
        assertNull(autoSink.error);
        assertNotNull(autoSink.done);
        String autoPrompt = readCapturedRequest();
        assertTrue(autoPrompt.contains("【学科规范（模型自选）】"));
        assertTrue(autoPrompt.contains("subject_get"), "AUTO 应把工具定义发给模型");
        assertTrue(autoPrompt.contains("DATA_STRUCTURE"));
        assertTrue(autoPrompt.contains("ADVANCED_MATH"));
        Integer bound = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_session WHERE id = ? AND subject_skill_id IS NOT NULL",
                Integer.class, autoSession);
        assertEquals(0, bound, "模型未调用工具时不应固化任何学科");

        STUB_REQUEST.set("");
        Long offSession = sessionService.create("IT-subject-off").id();
        sessionService.setSubject(offSession, "OFF", null);
        RecordingSink offSink = new RecordingSink();
        chatService.solve(new ChatCommand(offSession,
                "题目：判断栈与队列的性质", null, 0, List.of()), offSink);
        assertNull(offSink.error);
        assertNotNull(offSink.done);
        String offPrompt = readCapturedRequest();
        assertFalse(offPrompt.contains("【学科规范（模型自选）】"));
        assertFalse(offPrompt.contains("subject_get"));
        assertFalse(offPrompt.contains("【回答规范：高等数学"));
        assertTrue(offPrompt.contains("【回答规范：系统默认 · 解题】"));
    }

    /** CR-039 / AC-506-5：讲解轮按 explain 五段落库、intent=EXPLAIN、不抽知识点。 */
    @Test
    void explainIntentUsesExplainStructureAndSkipsKnowledgeExtraction() throws Exception {
        STUB_BODY.set(EXPLAIN_ANSWER);
        installStubConfig();
        STUB_INTENT.set("EXPLAIN");
        Long sessionId = sessionService.create("IT-explain").id();
        RecordingSink sink = new RecordingSink();
        chatService.solve(new ChatCommand(sessionId, "什么是后进先出？", null, 0, List.of()), sink);
        assertNull(sink.error);
        assertNotNull(sink.done);
        String prompt = readCapturedRequest();
        assertTrue(prompt.contains("【本轮问题】"), "讲解轮用「问题」前缀而非「题目」");
        assertTrue(prompt.contains("【回答规范：系统默认 · 讲解】"));
        for (String title : com.ai.studenthelper.common.SectionSchema.DEFAULT_EXPLAIN) {
            assertTrue(prompt.contains(title), "讲解默认五段：" + title);
        }
        List<Message> messages = messageRepository.findBySessionId(sessionId);
        assertEquals(2, messages.size());
        Message answer = messages.get(1);
        assertEquals("EXPLAIN", answer.getIntent());
        assertEquals("EXPLAIN", answer.getSectionSchema());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_knowledge_hit WHERE session_id = ?", Integer.class, sessionId),
                "讲解轮不抽知识点");
    }

    /** AC-501-9：追问走自由问答，不套六段、不加题目前缀与收口、不触发结构校验。 */
    @Test
    void followUpUsesFreeTextAndSkipsStructureValidation() throws Exception {
        STUB_BODY.set(FULL_ANSWER);
        installStubConfig();
        STUB_INTENT.set("SOLVE");
        Long sessionId = sessionService.create("IT-follow-up").id();
        RecordingSink first = new RecordingSink();
        chatService.solve(new ChatCommand(sessionId,
                "题目：判断栈与队列的性质", null, 0, List.of()), first);
        assertNotNull(first.done);

        STUB_BODY.set("因为栈只在栈顶插入删除，所以后入的元素必然先出；队列正好相反。");
        STUB_INTENT.set("FOLLOW_UP");
        STUB_REQUEST.set("");
        RecordingSink followUp = new RecordingSink();
        chatService.solve(new ChatCommand(sessionId,
                "为什么第二步要用这个性质？", null, 0, List.of()), followUp);
        assertNull(followUp.error);
        assertNotNull(followUp.done);
        String prompt = readCapturedRequest();
        assertTrue(prompt.contains("【本轮形态】自由问答"));
        assertTrue(prompt.contains("你是「大学课程」解题助手"),
                "CR-019：追问模板首行也应为学科通用称呼");
        // 历史里第一轮题面本身带「【本轮题目】」，因此只检查本轮最后一条用户消息
        var requestTree = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(STUB_REQUEST.get());
        var requestMessages = requestTree.path("messages");
        String lastUserMessage = requestMessages.get(requestMessages.size() - 1).toString();
        assertTrue(lastUserMessage.contains("为什么第二步要用这个性质？"));
        assertFalse(lastUserMessage.contains("【本轮题目】"), "追问不得加题目前缀");
        assertFalse(lastUserMessage.contains("请只回答本轮题目，严格遵守系统给定的段落结构"));

        List<Message> messages = messageRepository.findBySessionId(sessionId);
        assertEquals(4, messages.size());
        Message answer = messages.get(3);
        assertEquals("FREE_TEXT", answer.getSectionSchema());
        assertEquals("FOLLOW_UP", answer.getIntent());
        assertNull(answer.getSubjectLabel());
        assertFalse(answer.getContent().contains("## ①"));
    }

    // TCR-040 / AC-401-11：压缩开关与 memory 解耦。只开 conversationCompaction、
    // 关掉 memory 时解题仍正常，但 system prompt 不得出现长期记忆段。
    @Test
    void compactionFeatureAloneDoesNotLeakMemoryIntoPrompt() throws Exception {
        STUB_BODY.set(FULL_ANSWER);
        installStubConfig();
        memoryFileStore.updateUserSection("## 学习偏好\n- 只应出现在 memory 开启时", null);
        jdbcTemplate.update("UPDATE t_feature_toggle SET enabled = 0 WHERE feature_key = 'memory'");
        jdbcTemplate.update("UPDATE t_feature_toggle SET enabled = 1 WHERE feature_key = 'conversationCompaction'");

        Long session = sessionService.create("IT-compaction-only").id();
        RecordingSink sink = new RecordingSink();
        chatService.solve(new ChatCommand(session, "题目：判断栈与队列的性质", null, 0, List.of()), sink);

        assertNull(sink.error);
        assertNotNull(sink.done);
        String prompt = readCapturedRequest();
        assertFalse(prompt.contains("【长期记忆】"), "memory 关闭时不得注入长期记忆段");
        assertFalse(prompt.contains("只应出现在 memory 开启时"), "memory 关闭时不得读取记忆文件");
    }

    /** CR-039：分类桩返回 OpenAI 非流式最小响应。 */
    private static String stubIntentJson(String intent) {
        return "{\"id\":\"chatcmpl-stub-intent\",\"object\":\"chat.completion\",\"created\":0,"
                + "\"model\":\"stub-model\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + intent + " | stub\"},"
                + "\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
    }

    /** 解析后重新序列化，避免 SDK 把中文写成 unicode 转义造成字符串比对误判。 */
    private static String readCapturedRequest() throws Exception {
        String raw = STUB_REQUEST.get();
        assertFalse(raw.isBlank(), "记录桩应收到一次模型请求");
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw).toString();
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
                false, true, null, false, true);
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
