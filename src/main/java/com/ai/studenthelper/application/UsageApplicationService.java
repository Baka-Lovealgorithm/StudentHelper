package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.UsageDailyResponse;
import com.ai.studenthelper.common.dto.UsageSummaryResponse;
import com.ai.studenthelper.common.dto.UsageSummaryResult;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.UsageSummaryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 用量看板用例编排（T-206 / CR-014）：空数据返回空列表而非 500，窗口只接受 0 / 1 / 3 / 7。 */
@Service
public class UsageApplicationService {

    private final UsageSummaryRepository repository;
    private final S2FeatureService features;

    public UsageApplicationService(UsageSummaryRepository repository, S2FeatureService features) {
        this.repository = repository;
        this.features = features;
    }

    public UsageSummaryResult summary(Integer days) {
        features.requireEnabled(S2Feature.USAGE_PANEL);
        int window = normalize(days);
        List<UsageSummaryResponse> rows = repository.aggregate(window);
        List<UsageDailyResponse> daily = repository.daily(window);
        return new UsageSummaryResult(
                rows == null ? List.of() : List.copyOf(rows),
                daily == null ? List.of() : List.copyOf(daily),
                window);
    }

    private static int normalize(Integer days) {
        if (days == null || days == 0) {
            return 0;
        }
        if (days == 1 || days == 3 || days == 7) {
            return days;
        }
        throw new BusinessException(ErrorCode.E1004, "时间窗口仅支持最近 1 / 3 / 7 天或全部");
    }
}
