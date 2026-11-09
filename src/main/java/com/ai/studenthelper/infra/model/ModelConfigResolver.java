package com.ai.studenthelper.infra.model;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.ModelConfig;
import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import com.ai.studenthelper.infra.persistence.ModelConfigRepository;
import org.springframework.stereotype.Component;

/**
 * 读取当前生效配置并解密 API Key。每次请求都现读，不缓存（TD-12 / AC-105-2）。
 */
@Component
public class ModelConfigResolver {

    /** 已解密的当前配置快照，供 SolverAgentFactory 使用。 */
    public record ResolvedModelConfig(
            Long id,
            String baseUrl,
            String apiKey,
            String modelName,
            boolean visionSupported,
            boolean streamSupported,
            String extraOptions
    ) {
    }

    private final ModelConfigRepository repository;
    private final ApiKeyCipher cipher;

    public ModelConfigResolver(ModelConfigRepository repository, ApiKeyCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    public ResolvedModelConfig resolveCurrent() {
        ModelConfig config = repository.findCurrent()
                .orElseThrow(() -> new BusinessException(ErrorCode.E3002));
        return new ResolvedModelConfig(
                config.getId(),
                config.getBaseUrl(),
                cipher.decrypt(config.getApiKeyCipher()),
                config.getModelName(),
                config.getVisionSupported() != null && config.getVisionSupported() == 1,
                config.getStreamSupported() == null || config.getStreamSupported() == 1,
                config.getExtraOptions()
        );
    }
}
