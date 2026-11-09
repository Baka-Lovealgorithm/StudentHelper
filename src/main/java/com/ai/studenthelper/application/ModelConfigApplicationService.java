package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.ModelConfigRequest;
import com.ai.studenthelper.common.dto.ModelConfigResponse;
import com.ai.studenthelper.common.dto.ModelTemplateResponse;
import com.ai.studenthelper.domain.ModelConfig;
import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import com.ai.studenthelper.infra.persistence.ModelConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

/** 模型配置用例编排：增删改、脱敏列表、事务内切换当前配置（FR-105 / AC-105-x）。 */
@Service
public class ModelConfigApplicationService {

    private final ModelConfigRepository repository;
    private final ApiKeyCipher cipher;

    public ModelConfigApplicationService(ModelConfigRepository repository, ApiKeyCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    public List<ModelConfigResponse> list() {
        return repository.findAllOrdered().stream().map(this::toResponse).toList();
    }

    public List<ModelTemplateResponse> templates() {
        return ModelTemplates.all();
    }

    @Transactional
    public Long create(ModelConfigRequest request) {
        validate(request);
        LocalDateTime now = LocalDateTime.now();
        ModelConfig config = new ModelConfig();
        apply(config, request, now);
        config.setCreatedAt(now);
        config.setIsCurrent(0);
        config.setApiKeyCipher(encryptIfPresent(request.apiKey()));
        return repository.save(config).getId();
    }

    @Transactional
    public Long update(Long id, ModelConfigRequest request) {
        validate(request);
        ModelConfig config = requireConfig(id);
        apply(config, request, LocalDateTime.now());
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            config.setApiKeyCipher(cipher.encrypt(request.apiKey()));
        }
        return repository.save(config).getId();
    }

    @Transactional
    public void activate(Long id) {
        ModelConfig target = requireConfig(id);
        repository.clearCurrent();
        target.setIsCurrent(1);
        target.setUpdatedAt(LocalDateTime.now());
        repository.save(target);
    }

    @Transactional
    public void delete(Long id) {
        ModelConfig config = requireConfig(id);
        if (config.getIsCurrent() != null && config.getIsCurrent() == 1) {
            throw new BusinessException(ErrorCode.E3001);
        }
        repository.deleteById(id);
    }

    private ModelConfig requireConfig(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.E3003));
    }

    private void apply(ModelConfig config, ModelConfigRequest request, LocalDateTime now) {
        config.setName(request.name().trim());
        config.setProvider(request.provider().trim());
        config.setBaseUrl(request.baseUrl().trim());
        config.setModelName(request.modelName().trim());
        config.setVisionSupported(Boolean.TRUE.equals(request.visionSupported()) ? 1 : 0);
        config.setStreamSupported(request.streamSupported() == null || request.streamSupported() ? 1 : 0);
        config.setExtraOptions(request.extraOptions());
        config.setUpdatedAt(now);
    }

    private String encryptIfPresent(String apiKey) {
        return (apiKey == null || apiKey.isBlank()) ? null : cipher.encrypt(apiKey);
    }

    private ModelConfigResponse toResponse(ModelConfig c) {
        return new ModelConfigResponse(
                c.getId(), c.getName(), c.getProvider(), c.getBaseUrl(), c.getModelName(),
                c.getVisionSupported() != null && c.getVisionSupported() == 1,
                c.getStreamSupported() == null || c.getStreamSupported() == 1,
                c.getExtraOptions(),
                c.getIsCurrent() != null && c.getIsCurrent() == 1,
                c.getApiKeyCipher() != null && !c.getApiKeyCipher().isBlank(),
                c.getCreatedAt(), c.getUpdatedAt());
    }

    private void validate(ModelConfigRequest request) {
        if (request.modelName() == null || request.modelName().isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "modelName 不能为空");
        }
        if (request.visionSupported() == null) {
            throw new BusinessException(ErrorCode.E1004, "visionSupported 为必填项");
        }
        try {
            URI uri = URI.create(request.baseUrl());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E1004, "baseUrl 必须是合法的 http(s) 地址");
        }
    }
}
