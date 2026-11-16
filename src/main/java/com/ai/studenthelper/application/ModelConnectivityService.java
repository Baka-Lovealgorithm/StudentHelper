package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.ModelTestResult;
import com.ai.studenthelper.common.dto.ProviderTestResponse;
import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import com.ai.studenthelper.infra.persistence.ModelProviderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CR-020 模型连通性测试：用**真实最小对话请求**判定「Base URL + Key + 模型名」这条链路是否可用。
 *
 * <p>刻意与解题链路（{@code SolverAgentFactory} / {@code ModelConfigResolver}）完全隔离：
 * 不走 AgentScope、不合并模型条目的 {@code extra_options}、不写 {@code t_call_log}、
 * 不写 {@code t_message}、不进用量看板、不做任何缓存。测试失败一律以结果数据返回，
 * 只有「厂商 / 模型不存在」才抛业务异常。</p>
 */
@Service
public class ModelConnectivityService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单请求超时；与 {@link #OVERALL_TIMEOUT_MILLIS} 一起保证按钮不会长时间悬挂。 */
    private static final int REQUEST_TIMEOUT_SECONDS = 15;
    /** 厂商级并发上限：既避免 26 个模型串行数分钟，也避免把端点打出限流。 */
    private static final int MAX_CONCURRENCY = 4;
    /** 厂商级整体等待上限；超期未返回的项记失败。 */
    private static final long OVERALL_TIMEOUT_MILLIS = 40_000L;
    private static final int MAX_DETAIL_CHARS = 200;

    private final ModelProviderRepository repository;
    private final ApiKeyCipher cipher;
    private final HttpClient httpClient;
    private final int concurrency;

    @Autowired
    public ModelConnectivityService(ModelProviderRepository repository, ApiKeyCipher cipher) {
        this(repository, cipher,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), MAX_CONCURRENCY);
    }

    /** 测试用构造：允许注入桩 HttpClient 与并发上限。 */
    ModelConnectivityService(ModelProviderRepository repository, ApiKeyCipher cipher,
                             HttpClient httpClient, int concurrency) {
        this.repository = repository;
        this.cipher = cipher;
        this.httpClient = httpClient;
        this.concurrency = Math.max(1, concurrency);
    }

    /** 单个模型测试：真实发一次 {@code POST {baseUrl}/chat/completions}。 */
    public ModelTestResult testModel(Long providerId, Long modelId) {
        ModelProviderRepository.ProviderRow provider = requireProvider(providerId);
        ModelProviderRepository.ModelRow model = requireModel(providerId, modelId);
        return probe(provider, model);
    }

    /**
     * 厂商「全部测试」：遍历该厂商模型列表下的**全部模型**，并发上限 4、整体 40s 封顶。
     * 无模型时如实返回 {@code total=0}，不抛异常。
     */
    public ProviderTestResponse testProvider(Long providerId) {
        ModelProviderRepository.ProviderRow provider = requireProvider(providerId);
        List<ModelProviderRepository.ModelRow> models = repository.findModels().stream()
                .filter(m -> m.providerId().equals(providerId))
                .toList();
        if (models.isEmpty()) {
            return new ProviderTestResponse(providerId, provider.name(), 0, 0, 0, 0,
                    "该厂商下没有模型可测试", List.of());
        }

        long startedAt = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency, models.size()),
                daemonFactory());
        List<ModelTestResult> results = new ArrayList<>(models.size());
        try {
            List<Future<ModelTestResult>> futures = new ArrayList<>(models.size());
            for (ModelProviderRepository.ModelRow model : models) {
                futures.add(pool.submit(() -> probe(provider, model)));
            }
            for (int i = 0; i < futures.size(); i++) {
                results.add(await(futures.get(i), models.get(i), startedAt));
            }
        } finally {
            pool.shutdownNow();
        }

        int success = (int) results.stream().filter(ModelTestResult::ok).count();
        int failed = results.size() - success;
        long elapsed = System.currentTimeMillis() - startedAt;
        String message = failed == 0
                ? "全部 " + success + " 个模型连通正常"
                : success + " 个通过，" + failed + " 个失败";
        return new ProviderTestResponse(providerId, provider.name(), results.size(), success, failed,
                elapsed, message, results);
    }

    /** 到期限仍未算完的项记为失败，保证按钮在 40s 内一定拿到结果。 */
    private static ModelTestResult await(Future<ModelTestResult> future,
                                         ModelProviderRepository.ModelRow model, long startedAt) {
        long remaining = OVERALL_TIMEOUT_MILLIS - (System.currentTimeMillis() - startedAt);
        if (remaining <= 0) {
            future.cancel(true);
            return new ModelTestResult(model.id(), model.modelName(), false, null,
                    OVERALL_TIMEOUT_MILLIS, "等待超时未返回");
        }
        try {
            return future.get(remaining, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new ModelTestResult(model.id(), model.modelName(), false, null,
                    System.currentTimeMillis() - startedAt, "等待超时未返回");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ModelTestResult(model.id(), model.modelName(), false, null,
                    System.currentTimeMillis() - startedAt, "测试被中断");
        } catch (ExecutionException e) {
            return new ModelTestResult(model.id(), model.modelName(), false, null,
                    System.currentTimeMillis() - startedAt, "测试执行失败");
        }
    }

    /**
     * 最小对话请求：{@code max_tokens=16}、{@code temperature=0}、{@code stream=false}。
     * 不注入 {@code extra_options}——测试只验证标准链路，扩展参数由真实解题验证。
     */
    private ModelTestResult probe(ModelProviderRepository.ProviderRow provider,
                                  ModelProviderRepository.ModelRow model) {
        String url = chatCompletionsUrl(provider.baseUrl());
        long begin = System.nanoTime();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model.modelName());
            body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
            body.put("max_tokens", 16);
            body.put("temperature", 0);
            body.put("stream", false);

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
            String key;
            try {
                key = cipher.decrypt(provider.apiKeyCipher());
            } catch (RuntimeException e) {
                return fail(model, null, elapsed(begin), "API Key 解密失败，请重新填写 Key");
            }
            if (key != null && !key.isBlank()) {
                builder.header("Authorization", "Bearer " + key);
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latency = elapsed(begin);
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                return fail(model, status, latency, classifyStatus(status, response.body()));
            }
            if (!hasChoices(response.body())) {
                return fail(model, status, latency, "响应不是 OpenAI 兼容格式（缺少 choices）");
            }
            return new ModelTestResult(model.id(), model.modelName(), true, status, latency, "连通正常");
        } catch (HttpTimeoutException e) {
            return fail(model, null, elapsed(begin), "请求超时（>" + REQUEST_TIMEOUT_SECONDS + "s），请检查端点网络");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(model, null, elapsed(begin), "测试被中断");
        } catch (Exception e) {
            return fail(model, null, elapsed(begin),
                    "端点不可达，请检查 Base URL（" + e.getClass().getSimpleName() + "）");
        }
    }

    /** 失败原因按状态码归类；细节只保留截断片段，且不含 Key。 */
    private static String classifyStatus(int status, String responseBody) {
        if (status == 401 || status == 403) {
            return "鉴权失败，请检查 API Key";
        }
        if (status == 404) {
            return "模型或路径不存在，请检查模型名与 Base URL";
        }
        if (status == 429) {
            return "触发端点限流，请稍后重试";
        }
        if (status >= 500) {
            return "端点内部错误（HTTP " + status + "）";
        }
        String detail = truncate(responseBody);
        return detail.isEmpty() ? "端点返回 HTTP " + status : "端点返回 HTTP " + status + "：" + detail;
    }

    private static boolean hasChoices(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonNode choices = MAPPER.readTree(body).path("choices");
            return choices.isArray() && !choices.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 与 {@code fetchModels} 同一套归一化：去尾部斜杠；已写到 chat/completions 的不重复拼。 */
    static String chatCompletionsUrl(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= MAX_DETAIL_CHARS ? flat : flat.substring(0, MAX_DETAIL_CHARS) + "…";
    }

    private static long elapsed(long beginNanos) {
        return Math.max(0L, (System.nanoTime() - beginNanos) / 1_000_000L);
    }

    private static ModelTestResult fail(ModelProviderRepository.ModelRow model, Integer status,
                                        long latencyMs, String message) {
        return new ModelTestResult(model.id(), model.modelName(), false, status, latencyMs, message);
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "model-connectivity-test");
            thread.setDaemon(true);
            return thread;
        };
    }

    private ModelProviderRepository.ProviderRow requireProvider(Long id) {
        return repository.findProvider(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E3003, "模型厂商不存在"));
    }

    private ModelProviderRepository.ModelRow requireModel(Long providerId, Long modelId) {
        ModelProviderRepository.ModelRow model = repository.findModel(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E3003, "模型不存在"));
        if (!model.providerId().equals(providerId)) {
            throw new BusinessException(ErrorCode.E1004, "模型不属于该厂商");
        }
        return model;
    }
}
