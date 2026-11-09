package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.MemoryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 跨会话记忆条目（T-201）。停用用 enabled=0，便于清空而不丢审计痕迹。 */
@Repository
public class MemoryRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;

    public MemoryRepository(NamedParameterJdbcTemplate jdbc, JdbcTemplate plainJdbc) {
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
    }

    public Long insert(String kind, String content, Long sourceSessionId) {
        KeyHolder holder = new GeneratedKeyHolder();
        plainJdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO t_memory (kind, content, source_session_id, enabled, created_at) "
                            + "VALUES (?, ?, ?, 1, NOW(3))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, kind);
            ps.setString(2, content);
            if (sourceSessionId == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, sourceSessionId);
            }
            return ps;
        }, holder);
        return holder.getKey() == null ? null : holder.getKey().longValue();
    }

    public List<MemoryResponse> findAll() {
        return jdbc.query("SELECT id, kind, content, source_session_id, enabled, created_at "
                        + "FROM t_memory ORDER BY created_at DESC, id DESC",
                new MapSqlParameterSource(), (rs, i) -> new MemoryResponse(
                        rs.getLong("id"), rs.getString("kind"), rs.getString("content"),
                        nullableLong(rs.getObject("source_session_id")),
                        rs.getInt("enabled") == 1, timestamp(rs.getTimestamp("created_at"))));
    }

    /** 最近 N 条启用记忆，返回时按时间正序，保证注入顺序稳定。 */
    public List<String> findEnabledContents(int limit) {
        List<String> rows = jdbc.queryForList(
                "SELECT content FROM t_memory WHERE enabled = 1 "
                        + "ORDER BY created_at DESC, id DESC LIMIT " + Math.max(1, limit),
                new MapSqlParameterSource(), String.class);
        List<String> out = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            out.add(rows.get(i));
        }
        return out;
    }

    public void disable(Long id) {
        jdbc.update("UPDATE t_memory SET enabled = 0 WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    public void disableAll() {
        jdbc.update("UPDATE t_memory SET enabled = 0", new MapSqlParameterSource());
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
