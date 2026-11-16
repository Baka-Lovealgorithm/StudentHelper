package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.ModelTestResult;
import com.ai.studenthelper.common.dto.ProviderTestResponse;
import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import com.ai.studenthelper.infra.persistence.ModelProviderRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * CR-020 连通性测试：用本地 HTTP 桩跑真实出站请求路径，覆盖成功与各类失败归类。
 * 不发起任何真实付费调用（真实端点由人工在页面上点）。
 */
@ExtendWith(MockitoExtension.class)
class ModelConnectivityServiceTest {

    private static final String SUCCESS_BODY = "{\"id\":\"chatcmpl-stub\",\"choices\":"
            + "[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"pong\"}}]}";

    @Mock
    private ModelProviderRepository repository;

    private ApiKeyCipher cipher;
    private HttpServer server;
    private ModelConnectivityService service;
    private final AtomicReference<String> lastBody = new AtomicReference<>("");
    private final AtomicReference<String> lastAuth = new AtomicReference<>("");
    private volatile int status = 200;
    private volatile String responseBody = SUCCESS_BODY;

    @BeforeEach
    void setUp() throws IOException {
        cipher = new ApiKeyCipher("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
        service = new ModelConnectivityService(repository, cipher,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), 4);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private void givenProviderAndModel(String baseUrl, long modelId, String modelName) {
        when(repository.findProvider(1L)).thenReturn(Optional.of(new ModelProviderRepository.ProviderRow(
                1L, "桩厂商", baseUrl, cipher.encrypt("sk-stub-key"), null)));
        when(repository.findModel(modelId)).thenReturn(Optional.of(new ModelProviderRepository.ModelRow(
                modelId, 1L, modelName, false, true, null, false, false)));
    }

    @Test
    void successfulProbeSendsMinimalChatRequest() {
        givenProviderAndModel(baseUrl(), 11L, "m-1");

        ModelTestResult result = service.testModel(1L, 11L);

        assertTrue(result.ok(), result.message());
        assertEquals(200, result.httpStatus());
        assertEquals("连通正常", result.message());
        assertTrue(result.latencyMs() >= 0);
        assertEquals(11L, result.modelId());
        assertEquals("m-1", result.modelName());

        String body = lastBody.get();
        assertTrue(body.contains("\"max_tokens\":16"), body);
        assertTrue(body.contains("\"temperature\":0"), body);
        assertTrue(body.contains("\"stream\":false"), body);
        assertTrue(body.contains("\"model\":\"m-1\""), body);
        assertTrue(body.contains("\"content\":\"ping\""), body);
        // 请求体只含最小字段：不合并 extra_options
        assertFalse(body.contains("reasoning"), body);
        // Key 只出现在 Authorization 头
        assertEquals("Bearer sk-stub-key", lastAuth.get());
        assertFalse(body.contains("sk-stub-key"), "Key 不得进入请求体");
    }

    @Test
    void authFailureIsClassifiedWithoutLeakingKey() {
        status = 401;
        responseBody = "{\"error\":{\"message\":\"invalid api key\"}}";
        givenProviderAndModel(baseUrl(), 12L, "m-401");

        ModelTestResult result = service.testModel(1L, 12L);

        assertFalse(result.ok());
        assertEquals(401, result.httpStatus());
        assertTrue(result.message().contains("鉴权失败"), result.message());
        assertFalse(result.message().contains("sk-stub-key"));
    }

    @Test
    void notFoundIsClassified() {
        givenProviderAndModel("http://127.0.0.1:" + server.getAddress().getPort() + "/nope", 13L, "m-404");

        ModelTestResult result = service.testModel(1L, 13L);

        assertFalse(result.ok());
        assertEquals(404, result.httpStatus());
        assertTrue(result.message().contains("模型或路径不存在"), result.message());
    }

    @Test
    void serverErrorIsClassified() {
        status = 503;
        responseBody = "upstream unavailable";
        givenProviderAndModel(baseUrl(), 14L, "m-503");

        ModelTestResult result = service.testModel(1L, 14L);

        assertFalse(result.ok());
        assertTrue(result.message().contains("端点内部错误（HTTP 503）"), result.message());
    }

    @Test
    void nonCompatibleBodyIsDetected() {
        status = 200;
        responseBody = "{\"unexpected\":true}";
        givenProviderAndModel(baseUrl(), 15L, "m-weird");

        ModelTestResult result = service.testModel(1L, 15L);

        assertFalse(result.ok());
        assertEquals(200, result.httpStatus());
        assertTrue(result.message().contains("不是 OpenAI 兼容格式"), result.message());
    }

    @Test
    void unreachableEndpointIsReported() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        givenProviderAndModel("http://127.0.0.1:" + port + "/v1", 16L, "m-dead");

        ModelTestResult result = service.testModel(1L, 16L);

        assertFalse(result.ok());
        assertTrue(result.message().contains("端点不可达"), result.message());
    }

    @Test
    void providerTestCoversAllModelsAndKeepsOrder() {
        when(repository.findProvider(1L)).thenReturn(Optional.of(new ModelProviderRepository.ProviderRow(
                1L, "桩厂商", baseUrl(), cipher.encrypt("sk-stub-key"), null)));
        List<ModelProviderRepository.ModelRow> models = List.of(
                new ModelProviderRepository.ModelRow(21L, 1L, "ok-a", false, true, null, false, false),
                new ModelProviderRepository.ModelRow(22L, 1L, "bad-401", false, true, null, false, false),
                new ModelProviderRepository.ModelRow(23L, 1L, "ok-b", false, true, null, false, false),
                new ModelProviderRepository.ModelRow(24L, 9L, "other-provider", false, true, null, false, false));
        when(repository.findModels()).thenReturn(models);

        // 仅在模型名为 bad-401 时返回 401，验证按模型区分
        server.removeContext("/v1/chat/completions");
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastBody.set(body);
            boolean bad = body.contains("\"model\":\"bad-401\"");
            byte[] payload = (bad ? "{\"error\":\"unauthorized\"}" : SUCCESS_BODY)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(bad ? 401 : 200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });

        ProviderTestResponse response = service.testProvider(1L);

        assertEquals(3, response.total(), "只测该厂商下的模型");
        assertEquals(2, response.success());
        assertEquals(1, response.failed());
        assertEquals(3, response.results().size());
        assertEquals(List.of(21L, 22L, 23L),
                response.results().stream().map(ModelTestResult::modelId).toList(), "顺序与模型列表一致");
        assertTrue(response.results().get(1).message().contains("鉴权失败"));
        assertTrue(response.message().contains("2 个通过，1 个失败"), response.message());
        assertTrue(response.elapsedMs() >= 0);
    }

    @Test
    void providerWithoutModelsReturnsZeroWithoutError() {
        when(repository.findProvider(2L)).thenReturn(Optional.of(new ModelProviderRepository.ProviderRow(
                2L, "空厂商", "http://127.0.0.1:1/v1", null, null)));
        when(repository.findModels()).thenReturn(List.of());

        ProviderTestResponse response = service.testProvider(2L);

        assertEquals(0, response.total());
        assertEquals(0, response.success());
        assertEquals(0, response.failed());
        assertEquals("该厂商下没有模型可测试", response.message());
        assertTrue(response.results().isEmpty());
    }

    @Test
    void unknownProviderAndForeignModelAreRejected() {
        when(repository.findProvider(99L)).thenReturn(Optional.empty());
        BusinessException missingProvider = assertThrows(BusinessException.class,
                () -> service.testModel(99L, 1L));
        assertEquals(ErrorCode.E3003, missingProvider.getErrorCode());

        when(repository.findProvider(1L)).thenReturn(Optional.of(new ModelProviderRepository.ProviderRow(
                1L, "桩厂商", "http://127.0.0.1:1/v1", null, null)));
        when(repository.findModel(5L)).thenReturn(Optional.of(new ModelProviderRepository.ModelRow(
                5L, 8L, "foreign", false, true, null, false, false)));
        BusinessException foreign = assertThrows(BusinessException.class,
                () -> service.testModel(1L, 5L));
        assertEquals(ErrorCode.E1004, foreign.getErrorCode());
    }

    @Test
    void chatCompletionsUrlNormalizesTrailingSlashAndSuffix() {
        assertEquals("https://api.example.com/v1/chat/completions",
                ModelConnectivityService.chatCompletionsUrl("https://api.example.com/v1/"));
        assertEquals("https://api.example.com/v1/chat/completions",
                ModelConnectivityService.chatCompletionsUrl("https://api.example.com/v1"));
        assertEquals("https://api.example.com/v1/chat/completions",
                ModelConnectivityService.chatCompletionsUrl("https://api.example.com/v1/chat/completions"));
    }

    @Test
    void missingKeySendsNoAuthorizationHeader() {
        when(repository.findProvider(1L)).thenReturn(Optional.of(new ModelProviderRepository.ProviderRow(
                1L, "免鉴权端点", baseUrl(), null, null)));
        when(repository.findModel(31L)).thenReturn(Optional.of(new ModelProviderRepository.ModelRow(
                31L, 1L, "m-nokey", false, true, null, false, false)));

        ModelTestResult result = service.testModel(1L, 31L);

        assertTrue(result.ok(), result.message());
        assertEquals(null, lastAuth.get(), "无 Key 时不得发 Authorization 头");
        assertNotNull(result.message());
    }
}
