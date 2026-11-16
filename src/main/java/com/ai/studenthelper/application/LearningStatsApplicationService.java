package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.SolverPromptTemplate;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.LearningStatsResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.LearningStatsRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * T-305 学情统计：只读聚合，不新建统计表、不做金额换算、不引入图表库。
 *
 * <p>CR-023：① 窗口只接受 0（全部）/ 1 / 3 / 7，缺省 7；② 概览计数、按天柱状图、学科分布用**同一窗口**，
 * 保证「计数 = 柱状图各行之和」可对账；③ 不再返回 token 与耗时（归用量看板）。</p>
 */
@Service
public class LearningStatsApplicationService {

    /** 未传 days 时的默认窗口：最近 7 个自然日。 */
    private static final int DEFAULT_DAYS = 7;

    private final LearningStatsRepository repository;
    private final S2FeatureService features;

    /** CR-019：不再依赖旧学科服务，默认学科标签改用学科通用称呼。 */
    public LearningStatsApplicationService(LearningStatsRepository repository,
                                           S2FeatureService features) {
        this.repository = repository;
        this.features = features;
    }

    /** 兼容旧调用（内部无窗口诉求时按默认 7 天）。 */
    public LearningStatsResponse summary() {
        return summary(null);
    }

    public LearningStatsResponse summary(Integer days) {
        features.requireEnabled(S2Feature.LEARNING_STATS);
        int window = normalize(days);
        return new LearningStatsResponse(
                window,
                repository.sessions(window),
                repository.questions(window),
                repository.answers(window),
                repository.failedAnswers(window),
                SolverPromptTemplate.GENERIC_SUBJECT,
                nullToEmpty(repository.byDay(window)),
                nullToEmpty(repository.bySubject(window)));
    }

    private static int normalize(Integer days) {
        if (days == null) {
            return DEFAULT_DAYS;
        }
        if (days == 0 || days == 1 || days == 3 || days == 7) {
            return days;
        }
        throw new BusinessException(ErrorCode.E1004, "时间窗口仅支持最近 1 / 3 / 7 天或全部");
    }

    private static <T> List<T> nullToEmpty(List<T> rows) {
        return rows == null ? List.of() : List.copyOf(rows);
    }
}
