package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.SubjectResponse;
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

/** T-307 学科模板持久化。当前学科同一时刻只有一行 `is_current=1`。 */
@Repository
public class SubjectRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;

    public SubjectRepository(NamedParameterJdbcTemplate jdbc, JdbcTemplate plainJdbc) {
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
    }

    public List<SubjectResponse> findAll() {
        return jdbc.query("SELECT id, name, prompt_hint, is_current, created_at FROM t_subject "
                        + "ORDER BY is_current DESC, id ASC",
                new MapSqlParameterSource(), (rs, i) -> map(rs));
    }

    public Optional<SubjectResponse> findCurrent() {
        List<SubjectResponse> rows = jdbc.query(
                "SELECT id, name, prompt_hint, is_current, created_at FROM t_subject "
                        + "WHERE is_current = 1 ORDER BY id ASC LIMIT 1",
                new MapSqlParameterSource(), (rs, i) -> map(rs));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<SubjectResponse> findById(Long id) {
        List<SubjectResponse> rows = jdbc.query(
                "SELECT id, name, prompt_hint, is_current, created_at FROM t_subject WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> map(rs));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public boolean nameExists(String name, Long excludeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_subject WHERE name = :name"
                        + (excludeId == null ? "" : " AND id <> :exclude"),
                new MapSqlParameterSource().addValue("name", name).addValue("exclude", excludeId),
                Integer.class);
        return count != null && count > 0;
    }

    public Long insert(String name, String hint) {
        KeyHolder holder = new GeneratedKeyHolder();
        plainJdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO t_subject (name, prompt_hint, is_current, created_at, updated_at) "
                            + "VALUES (?, ?, 0, NOW(3), NOW(3))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, hint);
            return ps;
        }, holder);
        return holder.getKey() == null ? null : holder.getKey().longValue();
    }

    public void update(Long id, String name, String hint) {
        jdbc.update("UPDATE t_subject SET name = :name, prompt_hint = :hint, updated_at = NOW(3) "
                        + "WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name).addValue("hint", hint));
    }

    public void activate(Long id) {
        jdbc.update("UPDATE t_subject SET is_current = 0, updated_at = NOW(3) WHERE is_current = 1",
                new MapSqlParameterSource());
        jdbc.update("UPDATE t_subject SET is_current = 1, updated_at = NOW(3) WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM t_subject WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private static SubjectResponse map(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new SubjectResponse(rs.getLong("id"), rs.getString("name"), rs.getString("prompt_hint"),
                rs.getInt("is_current") == 1,
                created == null ? null : created.toLocalDateTime());
    }
}
