package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.KnowledgePointResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.KnowledgePointRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** T-203 知识点列表用例；关闭态拒绝查询（DD-01）。 */
@Service
public class KnowledgeApplicationService {

    private final KnowledgePointRepository repository;
    private final S2FeatureService features;

    public KnowledgeApplicationService(KnowledgePointRepository repository, S2FeatureService features) {
        this.repository = repository;
        this.features = features;
    }

    public List<KnowledgePointResponse> list(String subject) {
        features.requireEnabled(S2Feature.KNOWLEDGE_EXTRACT);
        return repository.list(subject);
    }
}
