package com.ai.studenthelper.infra.model;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.ModelConfig;
import com.ai.studenthelper.infra.crypto.ApiKeyCipher;
import com.ai.studenthelper.infra.persistence.ModelConfigRepository;
import com.ai.studenthelper.infra.persistence.ModelProviderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 读取生效的模型配置并解密 API Key。每次请求都现读，不缓存（TD-12 / AC-105-2）。
 *
 * <p>CR-012 起主链路走 {@link #resolveForSession(Long)}：会话模型 → 全局默认 → E3002；
 * 新表尚未迁移时回退 S1 旧表，保证平滑升级。{@link #resolveCurrent()} 保留旧语义，
 * 供 S1 单测与手动探针使用（只读 t_model_config.is_current）。
 */
@Component
public class ModelConfigResolver {

    /** 已解密的配置快照，供 SolverAgentFactory 使用；id 在 CR-012 后为模型条目 id。 */
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

    private final ModelConfigRepository legacyRepository;
    private final ApiKeyCipher cipher;
    private final ModelProviderRepository providerRepository;

    @Autowired
    public ModelConfigResolver(ModelConfigRepository legacyRepository, ApiKeyCipher cipher,
                               ModelProviderRepository providerRepository) {
        this.legacyRepository = legacyRepository;
        this.cipher = cipher;
        this.providerRepository = providerRepository;
    }

    /** 兼容 S1 单测与手动探针的两参构造（不接新表）。 */
    public ModelConfigResolver(ModelConfigRepository legacyRepository, ApiKeyCipher cipher) {
        this(legacyRepository, cipher, null);
    }

    /** CR-012 主链路入口：按会话所选模型解析，未选 / 已删则回退全局默认。 */
    public ResolvedModelConfig resolveForSession(Long sessionId) {
        if (providerRepository != null) {
            Long sessionModelId = sessionId == null ? null : providerRepository.findSessionModelId(sessionId);
            ResolvedModelConfig resolved = resolveByModelId(sessionModelId);
            if (resolved == null) {
                resolved = resolveByModelId(providerRepository.findDefaultModelId());
            }
            if (resolved != null) {
                return resolved;
            }
            if (providerRepository.countProviders() > 0) {
                throw new BusinessException(ErrorCode.E3002);
            }
        }
        // 新表尚未迁移（例如老环境首次启动前）时回退旧表，保证不出现「无可用模型」
        return resolveCurrent();
    }

    /** S1 遗留语义：读旧表 is_current。新链路不再调用。 */
    public ResolvedModelConfig resolveCurrent() {
        ModelConfig config = legacyRepository.findCurrent()
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

    private ResolvedModelConfig resolveByModelId(Long modelId) {
        if (modelId == null) {
            return null;
        }
        return providerRepository.resolve(modelId)
                .map(row -> new ResolvedModelConfig(
                        row.modelId(),
                        row.baseUrl(),
                        cipher.decrypt(row.apiKeyCipher()),
                        row.modelName(),
                        row.visionSupported(),
                        row.streamSupported(),
                        row.extraOptions()))
                .orElse(null);
    }
}
