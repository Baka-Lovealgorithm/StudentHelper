package com.ai.studenthelper.infra.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 单用户画像：应用层只维护一行（技术规范 5.5）。 */
@Repository
public class UserProfileRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public UserProfileRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String findJson() {
        List<String> rows = jdbc.queryForList(
                "SELECT profile_json FROM t_user_profile ORDER BY id LIMIT 1",
                new MapSqlParameterSource(), String.class);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void saveJson(String json) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM t_user_profile ORDER BY id LIMIT 1",
                new MapSqlParameterSource(), Long.class);
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("json", json);
        if (ids.isEmpty()) {
            jdbc.update("INSERT INTO t_user_profile (profile_json, updated_at) VALUES (:json, NOW(3))", p);
        } else {
            p.addValue("id", ids.get(0));
            jdbc.update("UPDATE t_user_profile SET profile_json = :json, updated_at = NOW(3) WHERE id = :id", p);
        }
    }

    public void clearJson() {
        jdbc.update("UPDATE t_user_profile SET profile_json = NULL, updated_at = NOW(3)",
                new MapSqlParameterSource());
    }
}
