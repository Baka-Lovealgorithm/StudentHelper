package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.FeatureToggleResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.FeatureToggleRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * S2 逐功能独立开关用例（DD-01）。默认关闭；关闭态下不写入任何 S2 表，
 * 主链路行为与计时口径不变。
 */
@Service
public class S2FeatureService {

    private final FeatureToggleRepository repository;

    public S2FeatureService(FeatureToggleRepository repository) {
        this.repository = repository;
    }

    public List<FeatureToggleResponse> list() {
        Map<String, Boolean> stored = repository.findAll();
        return Arrays.stream(S2Feature.values())
                .map(f -> new FeatureToggleResponse(f.key(), f.label(), f.task(),
                        Boolean.TRUE.equals(stored.getOrDefault(f.key(), false))))
                .toList();
    }

    public FeatureToggleResponse update(String key, Boolean enabled) {
        S2Feature feature = S2Feature.fromKey(key);
        if (feature == null || enabled == null) {
            throw new BusinessException(ErrorCode.E1004, "未知的功能开关或缺少 enabled 字段");
        }
        repository.setEnabled(feature.key(), enabled);
        return new FeatureToggleResponse(feature.key(), feature.label(), feature.task(), enabled);
    }

    public boolean isEnabled(S2Feature feature) {
        return repository.isEnabled(feature.key());
    }

    /** 关闭态统一拒绝，返回 E3004，避免静默写入 S2 数据。 */
    public void requireEnabled(S2Feature feature) {
        if (!isEnabled(feature)) {
            throw new BusinessException(ErrorCode.E3004,
                    "「" + feature.label() + "」当前已关闭，请在「功能开关」中开启后重试");
        }
    }
}
