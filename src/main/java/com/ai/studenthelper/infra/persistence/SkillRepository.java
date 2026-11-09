package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.SkillResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** T-302 解题技能持久化。技能是 Markdown 指令片段，启用后注入 System Prompt。 */
@Repository
public class SkillRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;

    public SkillRepository(NamedParameterJdbcTemplate jdbc, JdbcTemplate plainJdbc) {
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
    }

    public List<SkillResponse> findAll() {
        return jdbc.query("SELECT id, name, description, content, enabled, updated_at FROM t_skill "
                        + "ORDER BY enabled DESC, id ASC",
                new MapSqlParameterSource(), (rs, i) -> map(rs));
    }

    public List<SkillResponse> findEnabled(int limit) {
        return jdbc.query("SELECT id, name, description, content, enabled, updated_at FROM t_skill "
                        + "WHERE enabled = 1 ORDER BY id ASC LIMIT " + Math.max(1, limit),
                new MapSqlParameterSource(), (rs, i) -> map(rs));
    }

    public Optional<SkillResponse> findById(Long id) {
        List<SkillResponse> rows = jdbc.query(
                "SELECT id, name, description, content, enabled, updated_at FROM t_skill WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> map(rs));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public boolean nameExists(String name, Long excludeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_skill WHERE name = :name"
                        + (excludeId == null ? "" : " AND id <> :exclude"),
                new MapSqlParameterSource().addValue("name", name).addValue("exclude", excludeId),
                Integer.class);
        return count != null && count > 0;
    }

    public Long insert(String name, String description, String content, boolean enabled) {
        KeyHolder holder = new GeneratedKeyHolder();
        plainJdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO t_skill (name, description, content, enabled, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, NOW(3), NOW(3))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, content);
            ps.setInt(4, enabled ? 1 : 0);
            return ps;
        }, holder);
        return holder.getKey() == null ? null : holder.getKey().longValue();
    }

    public void update(Long id, String name, String description, String content, Boolean enabled) {
        jdbc.update("UPDATE t_skill SET name = :name, description = :description, content = :content, "
                        + "enabled = COALESCE(:enabled, enabled), updated_at = NOW(3) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name)
                        .addValue("description", description).addValue("content", content)
                        .addValue("enabled", enabled == null ? null : (enabled ? 1 : 0)));
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM t_skill WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private static SkillResponse map(ResultSet rs) throws SQLException {
        Timestamp updated = rs.getTimestamp("updated_at");
        return new SkillResponse(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("content"), rs.getInt("enabled") == 1,
                updated == null ? null : updated.toLocalDateTime());
    }
}
