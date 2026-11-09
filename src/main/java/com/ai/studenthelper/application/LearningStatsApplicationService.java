package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.LearningStatsResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.LearningStatsRepository;
import org.springframework.stereotype.Service;

/** T-305 学情统计：只读聚合，不新建统计表、不做金额换算、不引入图表库。 */
@Service
public class LearningStatsApplicationService {

    private final LearningStatsRepository repository;
    private final SubjectApplicationService subjects;
    private final S2FeatureService features;

    public LearningStatsApplicationService(LearningStatsRepository repository,
                                           SubjectApplicationService subjects,
                                           S2FeatureService features) {
        this.repository = repository;
        this.subjects = subjects;
        this.features = features;
    }

    public LearningStatsResponse summary() {
        features.requireEnabled(S2Feature.LEARNING_STATS);
        return new LearningStatsResponse(
                repository.sessions(),
                repository.questions(),
                repository.answers(),
                repository.failedAnswers(),
                repository.promptTokens(),
                repository.completionTokens(),
                repository.avgDurationMs(),
                subjects.currentForPrompt().name(),
                repository.byDay(),
                repository.bySubject());
    }
}
