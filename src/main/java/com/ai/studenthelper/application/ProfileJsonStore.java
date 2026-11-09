package com.ai.studenthelper.application;

import com.ai.studenthelper.infra.persistence.UserProfileRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * t_user_profile.profile_json 的唯一读写入口：T-201 画像与 T-204 可选段落子开关共用一行，
 * 统一在此合并，避免两个服务互相覆盖（DD-05 / DD-06）。
 */
@Component
public class ProfileJsonStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UserProfileRepository repository;

    public ProfileJsonStore(UserProfileRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> read() {
        String json = repository.findJson();
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(json, MAP_TYPE);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public void write(Map<String, Object> data) {
        try {
            repository.saveJson(MAPPER.writeValueAsString(data));
        } catch (Exception e) {
            throw new IllegalStateException("画像保存失败", e);
        }
    }

    public String raw() {
        return repository.findJson();
    }

    public void clear() {
        repository.clearJson();
    }
}
