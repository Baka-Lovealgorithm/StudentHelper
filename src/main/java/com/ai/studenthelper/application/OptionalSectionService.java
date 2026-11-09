package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.OptionalSectionsRequest;
import com.ai.studenthelper.common.dto.OptionalSectionsResponse;
import com.ai.studenthelper.domain.enums.S2Feature;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T-204 三个可选段落的子选项（DD-06）：主开关 optionalSections 之外，
 * 三个布尔存 t_user_profile.profile_json 的 optionalSections 对象。
 */
@Service
public class OptionalSectionService {

    private static final String KEY = "optionalSections";

    private final ProfileJsonStore store;
    private final S2FeatureService features;

    public OptionalSectionService(ProfileJsonStore store, S2FeatureService features) {
        this.store = store;
        this.features = features;
    }

    /** 主开关关闭时一律返回全 false，保证 Prompt 与 S1 逐字一致。 */
    public OptionalSectionsResponse current() {
        if (!features.isEnabled(S2Feature.OPTIONAL_SECTIONS)) {
            return new OptionalSectionsResponse(false, false, false);
        }
        Map<String, Object> raw = sectionMap();
        return new OptionalSectionsResponse(bool(raw.get("idea")),
                bool(raw.get("knowledge")), bool(raw.get("code")));
    }

    public OptionalSectionsResponse update(OptionalSectionsRequest request) {
        features.requireEnabled(S2Feature.OPTIONAL_SECTIONS);
        if (request == null) {
            throw new IllegalArgumentException("request required");
        }
        Map<String, Object> data = store.read();
        Map<String, Object> sections = sectionMap();
        if (request.idea() != null) {
            sections.put("idea", request.idea());
        }
        if (request.knowledge() != null) {
            sections.put("knowledge", request.knowledge());
        }
        if (request.code() != null) {
            sections.put("code", request.code());
        }
        data.put(KEY, sections);
        store.write(data);
        return current();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sectionMap() {
        Object value = store.read().get(KEY);
        return value instanceof Map ? new LinkedHashMap<>((Map<String, Object>) value)
                : new LinkedHashMap<>();
    }

    private static boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
