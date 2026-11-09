package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.UsageSummaryResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.UsageSummaryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 用量看板用例编排（T-206 / FR-206）：聚合结果透传，空数据返回空列表而非 500。 */
@Service
public class UsageApplicationService {

    private final UsageSummaryRepository repository;
    private final S2FeatureService features;

    public UsageApplicationService(UsageSummaryRepository repository, S2FeatureService features) {
        this.repository = repository;
        this.features = features;
    }

    public List<UsageSummaryResponse> summary() {
        features.requireEnabled(S2Feature.USAGE_PANEL);
        List<UsageSummaryResponse> rows = repository.aggregate();
        return rows == null ? List.of() : List.copyOf(rows);
    }
}
