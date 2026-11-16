package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.ProcessItem;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** CR-027：模型行为过程持久化；只作展示，不参与解题判定。 */
@Repository
public class ProcessRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ProcessRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 同一消息重复写入先删后插，保证 (message_id, seq) 幂等。 */
    public void saveAll(Long messageId, List<ProcessItem> items) {
        jdbc.update("DELETE FROM t_message_process WHERE message_id = :mid",
                new MapSqlParameterSource("mid", messageId));
        if (items == null || items.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = new MapSqlParameterSource[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ProcessItem item = items.get(i);
            batch[i] = new MapSqlParameterSource()
                    .addValue("mid", messageId)
                    .addValue("seq", item.seq())
                    .addValue("kind", cut(item.kind(), 24))
                    .addValue("title", cut(item.title(), 255))
                    .addValue("detail", item.detail())
                    .addValue("elapsed", item.elapsedMs());
        }
        jdbc.batchUpdate("INSERT INTO t_message_process "
                + "(message_id, seq, kind, title, detail, elapsed_ms) "
                + "VALUES (:mid, :seq, :kind, :title, :detail, :elapsed)", batch);
    }

    /** 供 GET /api/process?sessionId= 回读；按消息与 seq 升序，前端按 messageId 分组。 */
    public List<ProcessItem> listBySession(Long sessionId) {
        return jdbc.query("SELECT p.message_id, p.seq, p.kind, p.title, p.detail, p.elapsed_ms "
                        + "FROM t_message_process p JOIN t_message m ON m.id = p.message_id "
                        + "WHERE m.session_id = :sid ORDER BY p.message_id ASC, p.seq ASC",
                new MapSqlParameterSource("sid", sessionId),
                (rs, i) -> new ProcessItem(rs.getLong("message_id"), rs.getInt("seq"),
                        rs.getString("kind"), rs.getString("title"), rs.getString("detail"),
                        rs.getLong("elapsed_ms")));
    }

    /** 删除会话时先清过程行（SessionApplicationService 在删消息之前调用）。 */
    public void deleteBySessionId(Long sessionId) {
        jdbc.update("DELETE p FROM t_message_process p JOIN t_message m ON m.id = p.message_id "
                        + "WHERE m.session_id = :sid",
                new MapSqlParameterSource("sid", sessionId));
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
