package com.ai.studenthelper.infra.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/**
 * S2 功能开关持久化（DD-01）。每请求现读、不缓存；缺失行按「关闭」处理，
 * 保证即使未执行升级脚本，S2 功能也默认不可用（Gate-2 关闭态前提）。
 */
@Repository
public class FeatureToggleRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public FeatureToggleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Boolean> findAll() {
        Map<String, Boolean> result = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT feature_key, enabled FROM t_feature_toggle", Map.of())) {
            Object key = row.get("feature_key");
            Object enabled = row.get("enabled");
            if (key != null) {
                result.put(String.valueOf(key),
                        enabled instanceof Number number && number.intValue() == 1);
            }
        }
        return result;
    }

    public boolean isEnabled(String key) {
        Boolean value = jdbc.queryForObject(
                "SELECT enabled FROM t_feature_toggle WHERE feature_key = :key",
                new MapSqlParameterSource("key", key), Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    /** upsert：表缺行时插入，保证开关可被打开。 */
    public void setEnabled(String key, boolean enabled) {
        int affected = jdbc.update(
                "UPDATE t_feature_toggle SET enabled = :enabled, updated_at = NOW(3) WHERE feature_key = :key",
                new MapSqlParameterSource().addValue("key", key).addValue("enabled", enabled ? 1 : 0));
        if (affected == 0) {
            jdbc.update("INSERT INTO t_feature_toggle (feature_key, enabled, updated_at) "
                            + "VALUES (:key, :enabled, NOW(3))",
                    new MapSqlParameterSource().addValue("key", key).addValue("enabled", enabled ? 1 : 0));
        }
    }
}
