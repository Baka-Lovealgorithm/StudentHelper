package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.WrongBookResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** 错题本 = t_message_feedback 中 kind='WRONG' 的检索视图，不另建表（T-202）。 */
@Repository
public class WrongBookRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public WrongBookRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WrongBookResponse> query(String subject, LocalDateTime from, LocalDateTime to,
                                         int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT f.id AS feedback_id, f.message_id, f.session_id, f.subject, f.note, "
                        + "f.created_at, m.content AS question "
                        + "FROM t_message_feedback f JOIN t_message m ON m.id = f.message_id "
                        + "WHERE f.kind = 'WRONG'");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (subject != null && !subject.isBlank()) {
            sql.append(" AND f.subject = :subject");
            p.addValue("subject", subject.trim());
        }
        if (from != null) {
            sql.append(" AND f.created_at >= :from");
            p.addValue("from", Timestamp.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND f.created_at <= :to");
            p.addValue("to", Timestamp.valueOf(to));
        }
        sql.append(" ORDER BY f.created_at DESC, f.id DESC LIMIT :limit OFFSET :offset");
        p.addValue("limit", size).addValue("offset", page * size);
        return jdbc.query(sql.toString(), p, (rs, i) -> new WrongBookResponse(
                rs.getLong("feedback_id"), rs.getLong("message_id"), rs.getLong("session_id"),
                rs.getString("question"), rs.getString("subject"), rs.getString("note"),
                rs.getTimestamp("created_at") == null ? null
                        : rs.getTimestamp("created_at").toLocalDateTime()));
    }
}
