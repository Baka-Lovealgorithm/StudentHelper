package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.MemoryRequest;
import com.ai.studenthelper.common.dto.MemoryResponse;
import com.ai.studenthelper.common.dto.ProfileResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.MemoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * T-201 跨会话长期记忆与用户画像（DD-05：显式写入为主，不自动抽取、不追加模型调用）。
 */
@Service
public class ProfileApplicationService {

    private static final Set<String> KINDS = Set.of("PREFERENCE", "FACT", "SUMMARY");

    private final ProfileJsonStore store;
    private final MemoryRepository memoryRepository;
    private final S2FeatureService features;

    public ProfileApplicationService(ProfileJsonStore store, MemoryRepository memoryRepository,
                                     S2FeatureService features) {
        this.store = store;
        this.memoryRepository = memoryRepository;
        this.features = features;
    }

    public ProfileResponse profile() {
        return new ProfileResponse(features.isEnabled(S2Feature.MEMORY) ? store.raw() : null);
    }

    /** 写入/合并画像字段；关闭态拒绝，不写任何 S2 表。 */
    public ProfileResponse updateProfile(Map<String, Object> patch) {
        features.requireEnabled(S2Feature.MEMORY);
        if (patch == null || patch.isEmpty()) {
            throw new BusinessException(ErrorCode.E1004, "画像内容不能为空");
        }
        Map<String, Object> data = store.read();
        patch.forEach((key, value) -> {
            // CR-024：可选输出段落已退役，不再需要对 profile_json 里的 optionalSections 做特殊保护
            if (key != null && !key.isBlank()) {
                data.put(key, value);
            }
        });
        store.write(data);
        return new ProfileResponse(store.raw());
    }

    public void clearProfile() {
        features.requireEnabled(S2Feature.MEMORY);
        store.clear();
        memoryRepository.disableAll();
    }

    public MemoryResponse addMemory(MemoryRequest request) {
        features.requireEnabled(S2Feature.MEMORY);
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.E1004, "记忆内容不能为空");
        }
        String kind = request.kind() == null ? "PREFERENCE"
                : request.kind().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) {
            throw new BusinessException(ErrorCode.E1004, "记忆类型必须是 PREFERENCE / FACT / SUMMARY");
        }
        String content = request.content().trim();
        if (content.length() > 500) {
            content = content.substring(0, 500);
        }
        Long id = memoryRepository.insert(kind, content, request.sourceSessionId());
        return memoryRepository.findAll().stream()
                .filter(m -> m.id().equals(id)).findFirst().orElse(null);
    }

    public List<MemoryResponse> memories() {
        return features.isEnabled(S2Feature.MEMORY)
                ? memoryRepository.findAll() : List.of();
    }

    public void disableMemory(Long id) {
        features.requireEnabled(S2Feature.MEMORY);
        memoryRepository.disable(id);
    }
}
