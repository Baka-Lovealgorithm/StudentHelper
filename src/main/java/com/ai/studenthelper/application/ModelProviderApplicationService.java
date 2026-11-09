package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.FetchModelsResponse;
import com.ai.studenthelper.common.dto.ModelEntryRequest;
import com.ai.studenthelper.common.dto.ModelProviderResponse;
import com.ai.studenthelper.common.dto.ProviderListResponse;
import com.ai.studenthelper.common.dto.ProviderModelResponse;
import com.ai.studenthelper.common.dto.ProviderOptionsResponse;
import com.ai.studenthelper.common.dto.ProviderRequest;
import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import com.ai.studenthelper.infra.persistence.ModelProviderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * CR-012 通用厂商配置用例：厂商只配名称 / Base URL / Key，模型单独挂在其下，
 * 视觉 / 流式 / 扩展参数都在模型条目级；解题链路按「会话模型 → 全局默认」解析。
 */
@Service
public class ModelProviderApplicationService {

    private static final int MAX_NAME = 100;
    private static final int MAX_MODEL_NAME = 200;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ModelProviderRepository repository;
    private final ApiKeyCipher cipher;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public ModelProviderApplicationService(ModelProviderRepository repository, ApiKeyCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    public ProviderListResponse list() {
        List<ModelProviderRepository.ModelRow> models = repository.findModels();
        Map<Long, List<ProviderModelResponse>> grouped = new LinkedHashMap<>();
        for (ModelProviderRepository.ModelRow m : models) {
            grouped.computeIfAbsent(m.providerId(), key -> new ArrayList<>())
                    .add(toModelResponse(m));
        }
        List<ModelProviderResponse> providers = repository.findProviders().stream()
                .map(p -> new ModelProviderResponse(p.id(), p.name(), p.baseUrl(), hasKey(p.apiKeyCipher()),
                        grouped.getOrDefault(p.id(), List.of()), p.updatedAt()))
                .toList();
        return new ProviderListResponse(providers, repository.findDefaultModelId());
    }

    /** 对话页两级下拉：只给 id 与名字，不给 Key 与扩展参数。 */
    public ProviderOptionsResponse options() {
        List<ModelProviderRepository.ModelRow> models = repository.findModels();
        Map<Long, List<ProviderOptionsResponse.Model>> grouped = new LinkedHashMap<>();
        for (ModelProviderRepository.ModelRow m : models) {
            grouped.computeIfAbsent(m.providerId(), key -> new ArrayList<>())
                    .add(new ProviderOptionsResponse.Model(m.id(), m.modelName()));
        }
        List<ProviderOptionsResponse.Option> options = repository.findProviders().stream()
                .map(p -> new ProviderOptionsResponse.Option(p.id(), p.name(),
                        grouped.getOrDefault(p.id(), List.of())))
                .toList();
        return new ProviderOptionsResponse(options, repository.findDefaultModelId());
    }

    @Transactional
    public Long createProvider(ProviderRequest request) {
        String name = normalizeName(request.name());
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        if (repository.providerNameExists(name, null)) {
            throw new BusinessException(ErrorCode.E1004, "厂商名称已存在：" + name);
        }
        return repository.insertProvider(name, baseUrl, encryptIfPresent(request.apiKey()));
    }

    @Transactional
    public void updateProvider(Long id, ProviderRequest request) {
        ModelProviderRepository.ProviderRow existing = requireProvider(id);
        String name = normalizeName(request.name());
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        if (repository.providerNameExists(name, id)) {
            throw new BusinessException(ErrorCode.E1004, "厂商名称已存在：" + name);
        }
        String keyCipher = (request.apiKey() != null && !request.apiKey().isBlank())
                ? cipher.encrypt(request.apiKey()) : existing.apiKeyCipher();
        repository.updateProvider(id, name, baseUrl, keyCipher);
    }

    @Transactional
    public void deleteProvider(Long id) {
        requireProvider(id);
        for (ModelProviderRepository.ModelRow model : repository.findModels()) {
            if (model.providerId().equals(id)) {
                repository.clearSessionModel(model.id());
            }
        }
        repository.deleteModelsByProvider(id);
        repository.deleteProvider(id);
        ensureDefaultExists();
    }

    /**
     * 后端代理获取端点模型列表：Key 只在 Authorization 头里出现，
     * 不写入 URL、响应体或日志；失败返回中文 error，前端仍可手动添加。
     */
    public FetchModelsResponse fetchModels(Long id) {
        ModelProviderRepository.ProviderRow provider = requireProvider(id);
        String base = provider.baseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base.endsWith("/models") ? base : base + "/models";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET();
            String key = cipher.decrypt(provider.apiKeyCipher());
            if (key != null && !key.isBlank()) {
                builder.header("Authorization", "Bearer " + key);
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new FetchModelsResponse(List.of(), "端点返回 HTTP " + response.statusCode() + "，可手动添加模型名");
            }
            JsonNode data = MAPPER.readTree(response.body()).path("data");
            if (!data.isArray()) {
                return new FetchModelsResponse(List.of(), "端点未返回模型数组，可手动添加模型名");
            }
            TreeSet<String> names = new TreeSet<>();
            for (JsonNode node : data) {
                String modelId = node.path("id").asText(null);
                if (modelId != null && !modelId.isBlank()) {
                    names.add(modelId);
                }
            }
            return new FetchModelsResponse(new ArrayList<>(names), null);
        } catch (Exception e) {
            return new FetchModelsResponse(List.of(),
                    "获取模型列表失败：" + e.getClass().getSimpleName() + "，可手动添加模型名");
        }
    }

    /** 批量勾选添加（只给名字，属性取默认）或单个带属性添加。 */
    @Transactional
    public List<ProviderModelResponse> addModels(Long providerId, ModelEntryRequest request) {
        requireProvider(providerId);
        List<String> names = new ArrayList<>();
        boolean batch = request.modelNames() != null && !request.modelNames().isEmpty();
        if (batch) {
            for (String raw : request.modelNames()) {
                if (raw != null && !raw.isBlank()) {
                    names.add(normalizeModelName(raw));
                }
            }
        } else {
            names.add(normalizeModelName(request.modelName()));
        }
        if (names.isEmpty()) {
            throw new BusinessException(ErrorCode.E1004, "模型名不能为空");
        }
        TreeSet<String> unique = new TreeSet<>(names);
        boolean vision = Boolean.TRUE.equals(request.visionSupported());
        boolean stream = request.streamSupported() == null || request.streamSupported();
        List<ProviderModelResponse> added = new ArrayList<>();
        for (String name : unique) {
            if (repository.modelNameExists(providerId, name, null)) {
                if (!batch) {
                    throw new BusinessException(ErrorCode.E1004, "该厂商下模型已存在：" + name);
                }
                continue;
            }
            Long id = repository.insertModel(providerId, name, vision, stream, request.extraOptions(), false);
            added.add(new ProviderModelResponse(id, providerId, name, vision, stream,
                    request.extraOptions(), false));
        }
        if (repository.findDefaultModelId() == null && !added.isEmpty()) {
            repository.clearDefault();
            repository.setDefault(added.get(0).id());
        }
        return added;
    }

    @Transactional
    public ProviderModelResponse updateModel(Long providerId, Long modelId, ModelEntryRequest request) {
        requireProvider(providerId);
        ModelProviderRepository.ModelRow model = requireModel(providerId, modelId);
        String name = request.modelName() == null || request.modelName().isBlank()
                ? model.modelName() : normalizeModelName(request.modelName());
        if (repository.modelNameExists(providerId, name, modelId)) {
            throw new BusinessException(ErrorCode.E1004, "该厂商下模型已存在：" + name);
        }
        boolean vision = request.visionSupported() == null ? model.visionSupported() : request.visionSupported();
        boolean stream = request.streamSupported() == null ? model.streamSupported() : request.streamSupported();
        String extra = request.extraOptions() == null ? model.extraOptions() : request.extraOptions();
        repository.updateModel(modelId, name, vision, stream, extra);
        return new ProviderModelResponse(modelId, providerId, name, vision, stream, extra, model.isDefault());
    }

    @Transactional
    public void deleteModel(Long providerId, Long modelId) {
        requireProvider(providerId);
        ModelProviderRepository.ModelRow model = requireModel(providerId, modelId);
        repository.clearSessionModel(modelId);
        repository.deleteModel(modelId);
        if (model.isDefault()) {
            ensureDefaultExists();
        }
    }

    @Transactional
    public void setDefault(Long providerId, Long modelId) {
        requireProvider(providerId);
        requireModel(providerId, modelId);
        repository.clearDefault();
        repository.setDefault(modelId);
    }

    /** 供会话切换模型时校验：模型必须存在且属于可用厂商。 */
    public ModelProviderRepository.ModelRow requireUsableModel(Long modelId) {
        if (modelId == null) {
            throw new BusinessException(ErrorCode.E1004, "modelId 不能为空");
        }
        return repository.findModel(modelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E3003, "模型不存在或已被删除"));
    }

    private void ensureDefaultExists() {
        if (repository.findDefaultModelId() != null) {
            return;
        }
        repository.findModels().stream().findFirst().ifPresent(next -> {
            repository.clearDefault();
            repository.setDefault(next.id());
        });
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

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "厂商名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME) {
            throw new BusinessException(ErrorCode.E1004, "厂商名称不超过 " + MAX_NAME + " 字");
        }
        return trimmed;
    }

    private static String normalizeModelName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "模型名不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_MODEL_NAME) {
            throw new BusinessException(ErrorCode.E1004, "模型名不超过 " + MAX_MODEL_NAME + " 字");
        }
        return trimmed;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "Base URL 不能为空");
        }
        String trimmed = baseUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E1004, "Base URL 必须是合法的 http(s) 地址");
        }
        return trimmed;
    }

    private String encryptIfPresent(String apiKey) {
        return (apiKey == null || apiKey.isBlank()) ? null : cipher.encrypt(apiKey);
    }

    private static boolean hasKey(String cipher) {
        return cipher != null && !cipher.isBlank();
    }

    private static ProviderModelResponse toModelResponse(ModelProviderRepository.ModelRow m) {
        return new ProviderModelResponse(m.id(), m.providerId(), m.modelName(), m.visionSupported(),
                m.streamSupported(), m.extraOptions(), m.isDefault());
    }
}
