package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.SolverPromptTemplate;
import com.ai.studenthelper.config.MemoryProperties;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.memory.MemoryFileStore;
import org.springframework.stereotype.Service;

/**
 * T-401：把 {@code MEMORY.md} 组装成 system prompt 增量。
 * 开关关闭或文件为空时返回空串，保证关闭态与 S1 base 逐字一致（AC-401-2 / AC-204-1）。
 */
@Service
public class MemoryInjectionService {

    private final MemoryFileStore store;
    private final S2FeatureService features;
    private final MemoryProperties properties;

    public MemoryInjectionService(MemoryFileStore store, S2FeatureService features,
                                  MemoryProperties properties) {
        this.store = store;
        this.features = features;
        this.properties = properties;
    }

    /** 返回可直接追加到 system prompt 的整段；无需注入时返回空串。 */
    public String block() {
        if (!features.isEnabled(S2Feature.MEMORY)) {
            return "";
        }
        String content = store.mergedForInjection(properties.getInjectMaxChars());
        return content.isBlank() ? "" : SolverPromptTemplate.longTermMemoryBlock(content);
    }
}
