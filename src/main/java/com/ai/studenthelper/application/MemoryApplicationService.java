package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.MemoryOverviewResponse;
import com.ai.studenthelper.common.dto.MemoryUserUpdateRequest;
import com.ai.studenthelper.config.MemoryProperties;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.memory.MemoryFileStore;
import org.springframework.stereotype.Service;

/**
 * T-401：长期记忆读写编排。开关关闭时读操作返回空、写操作返回 E3004，
 * 且不触碰任何记忆文件（AC-401-2 / DD-01 关闭态口径）。
 */
@Service
public class MemoryApplicationService {

    private final MemoryFileStore store;
    private final MemoryProperties properties;
    private final S2FeatureService features;
    private final MemoryConsolidationService consolidation;

    public MemoryApplicationService(MemoryFileStore store, MemoryProperties properties,
                                    S2FeatureService features,
                                    MemoryConsolidationService consolidation) {
        this.store = store;
        this.properties = properties;
        this.features = features;
        this.consolidation = consolidation;
    }

    public MemoryOverviewResponse overview() {
        if (!features.isEnabled(S2Feature.MEMORY)) {
            return new MemoryOverviewResponse("", "", "", properties.getInjectMaxChars(), java.util.List.of());
        }
        MemoryFileStore.MemorySections sections = store.read();
        return new MemoryOverviewResponse(sections.user(), sections.model(), sections.version(),
                properties.getInjectMaxChars(), store.fileInfos());
    }

    public String readFile(String name) {
        if (!features.isEnabled(S2Feature.MEMORY)) {
            return "";
        }
        try {
            return store.readByName(name);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E1004, e.getMessage());
        }
    }

    public MemoryOverviewResponse updateUserSection(MemoryUserUpdateRequest request) {
        features.requireEnabled(S2Feature.MEMORY);
        if (request == null || request.content() == null) {
            throw new BusinessException(ErrorCode.E1004, "记忆内容不能为空");
        }
        try {
            store.updateUserSection(request.content(), request.version());
        } catch (MemoryFileStore.MemoryConflictException e) {
            throw new BusinessException(ErrorCode.E1004, e.getMessage());
        }
        return overview();
    }

    public MemoryConsolidationService.ConsolidationResult consolidate() {
        features.requireEnabled(S2Feature.MEMORY);
        return consolidation.consolidate(true);
    }

    public void clear() {
        features.requireEnabled(S2Feature.MEMORY);
        store.clear();
    }
}
