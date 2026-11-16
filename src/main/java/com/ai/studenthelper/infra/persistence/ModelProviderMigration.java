package com.ai.studenthelper.infra.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CR-012 数据迁移（upgrade-s4.sql 的配套）：把 S1 的 t_model_config 搬到
 * t_model_provider / t_provider_model。
 *
 * <p>幂等条件：新表已有厂商则直接跳过。全程在事务内完成，中途失败整体回滚，
 * 下次启动可重试。api_key_cipher **密文原样复制**——新旧表共用同一主密钥，
 * 现有 ApiKeyCipher 能直接解密，因此全程不解密、不回显明文、用户无需重填 Key。
 */
@Component
public class ModelProviderMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelProviderMigration.class);

    private final ModelProviderRepository repository;
    private final TransactionTemplate tx;

    public ModelProviderMigration(ModelProviderRepository repository, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tx.executeWithoutResult(status -> migrateIfEmpty());
        } catch (Exception e) {
            // 迁移失败不阻断应用启动：旧链路仍可用，日志中可直接看到原因
            log.error("MODEL_PROVIDER_MIGRATION_FAILED 旧模型配置迁移失败，可修复后重启重试", e);
        }
    }

    private void migrateIfEmpty() {
        if (repository.countProviders() > 0) {
            return;
        }
        List<ModelProviderRepository.LegacyRow> rows = repository.findLegacyConfigs();
        if (rows.isEmpty()) {
            return;
        }
        Map<String, List<ModelProviderRepository.LegacyRow>> groups = new LinkedHashMap<>();
        for (ModelProviderRepository.LegacyRow row : rows) {
            groups.computeIfAbsent(row.baseUrl(), key -> new ArrayList<>()).add(row);
        }

        Long globalDefault = null;
        int providerCount = 0;
        int modelCount = 0;
        for (Map.Entry<String, List<ModelProviderRepository.LegacyRow>> entry : groups.entrySet()) {
            List<ModelProviderRepository.LegacyRow> group = entry.getValue();
            ModelProviderRepository.LegacyRow first = group.get(0);
            String name = first.name();
            if (repository.providerNameExists(name, null)) {
                name = name + " #" + first.id();
                int suffix = 1;
                while (repository.providerNameExists(name, null)) {
                    name = first.name() + " #" + first.id() + "-" + (++suffix);
                }
            }
            // 同一端点只保留一份 Key（取组内第一条），密文不做任何解密
            Long providerId = repository.insertProvider(name, entry.getKey(), first.apiKeyCipher());
            providerCount++;

            Long firstModelId = null;
            Long currentModelId = null;
            for (ModelProviderRepository.LegacyRow row : group) {
                if (repository.modelNameExists(providerId, row.modelName(), null)) {
                    continue;
                }
                Long modelId = repository.insertModel(providerId, row.modelName(),
                        row.visionSupported() == 1, row.streamSupported() == 1, row.extraOptions(),
                        false, false);
                modelCount++;
                if (firstModelId == null) {
                    firstModelId = modelId;
                }
                if (row.isCurrent() == 1 && currentModelId == null) {
                    currentModelId = modelId;
                }
            }
            if (globalDefault == null) {
                globalDefault = currentModelId != null ? currentModelId : firstModelId;
            }
        }
        if (globalDefault != null) {
            repository.clearDefault();
            repository.setDefault(globalDefault);
            repository.backfillSessions(globalDefault);
        }
        log.info("MODEL_PROVIDER_MIGRATED legacyRows={} providers={} models={} defaultModelId={} keyCopiedAsCipher=true",
                rows.size(), providerCount, modelCount, globalDefault);
    }
}
