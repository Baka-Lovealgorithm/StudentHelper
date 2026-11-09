package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.McpServerResponse;
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

/** T-301 远程 MCP Server 持久化（只存 URL，不存任何凭据）。 */
@Repository
public class McpServerRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;

    public McpServerRepository(NamedParameterJdbcTemplate jdbc, JdbcTemplate plainJdbc) {
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
    }

    public List<McpServerResponse> findAll() {
        return jdbc.query("SELECT id, name, transport, url, enabled, last_status, last_error, "
                        + "tool_count, updated_at FROM t_mcp_server ORDER BY id ASC",
                new MapSqlParameterSource(), (rs, i) -> map(rs));
    }

    public List<McpServerResponse> findEnabled(int limit) {
        return jdbc.query("SELECT id, name, transport, url, enabled, last_status, last_error, "
                        + "tool_count, updated_at FROM t_mcp_server WHERE enabled = 1 "
                        + "ORDER BY id ASC LIMIT " + Math.max(1, limit),
                new MapSqlParameterSource(), (rs, i) -> map(rs));
    }

    public Optional<McpServerResponse> findById(Long id) {
        List<McpServerResponse> rows = jdbc.query(
                "SELECT id, name, transport, url, enabled, last_status, last_error, tool_count, "
                        + "updated_at FROM t_mcp_server WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> map(rs));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public boolean nameExists(String name, Long excludeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_mcp_server WHERE name = :name"
                        + (excludeId == null ? "" : " AND id <> :exclude"),
                new MapSqlParameterSource().addValue("name", name).addValue("exclude", excludeId),
                Integer.class);
        return count != null && count > 0;
    }

    public Long insert(String name, String transport, String url, boolean enabled) {
        KeyHolder holder = new GeneratedKeyHolder();
        plainJdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO t_mcp_server (name, transport, url, enabled, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, NOW(3), NOW(3))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, transport);
            ps.setString(3, url);
            ps.setInt(4, enabled ? 1 : 0);
            return ps;
        }, holder);
        return holder.getKey() == null ? null : holder.getKey().longValue();
    }

    public void update(Long id, String name, String transport, String url, Boolean enabled) {
        jdbc.update("UPDATE t_mcp_server SET name = :name, transport = :transport, url = :url, "
                        + "enabled = COALESCE(:enabled, enabled), updated_at = NOW(3) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name)
                        .addValue("transport", transport).addValue("url", url)
                        .addValue("enabled", enabled == null ? null : (enabled ? 1 : 0)));
    }

    public void updateStatus(Long id, String status, String error, int toolCount) {
        jdbc.update("UPDATE t_mcp_server SET last_status = :status, last_error = :error, "
                        + "tool_count = :count, updated_at = NOW(3) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("status", status)
                        .addValue("error", error).addValue("count", toolCount));
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM t_mcp_server WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    private static McpServerResponse map(ResultSet rs) throws SQLException {
        Timestamp updated = rs.getTimestamp("updated_at");
        return new McpServerResponse(rs.getLong("id"), rs.getString("name"), rs.getString("transport"),
                rs.getString("url"), rs.getInt("enabled") == 1, rs.getString("last_status"),
                rs.getString("last_error"), rs.getInt("tool_count"),
                updated == null ? null : updated.toLocalDateTime());
    }
}
