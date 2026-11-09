package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.PracticeResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/** T-205 举一反三四步状态（PENDING -> PASS / FAIL）。 */
@Repository
public class PracticeRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;

    public PracticeRepository(NamedParameterJdbcTemplate jdbc, JdbcTemplate plainJdbc) {
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
    }

    /** 用 GeneratedKeyHolder 取自增 id，避免连接池下 LAST_INSERT_ID() 拿错连接。 */
    public Long create(Long sourceMessageId, Long sessionId, Long kpId, String variantText) {
        KeyHolder holder = new GeneratedKeyHolder();
        plainJdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO t_practice "
                            + "(source_message_id, session_id, kp_id, variant_text, judge_result, created_at) "
                            + "VALUES (?, ?, ?, ?, 'PENDING', NOW(3))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, sourceMessageId);
            ps.setLong(2, sessionId);
            if (kpId == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, kpId);
            }
            ps.setString(4, variantText);
            return ps;
        }, holder);
        return holder.getKey() == null ? null : holder.getKey().longValue();
    }

    public PracticeResponse findById(Long id) {
        List<PracticeResponse> rows = jdbc.query("SELECT * FROM t_practice WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> map(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<PracticeResponse> findBySessionId(Long sessionId) {
        return jdbc.query("SELECT * FROM t_practice WHERE session_id = :s ORDER BY id DESC",
                new MapSqlParameterSource("s", sessionId), (rs, i) -> map(rs));
    }

    public void saveAnswer(Long id, String answer) {
        jdbc.update("UPDATE t_practice SET user_answer = :a WHERE id = :id AND judge_result = 'PENDING'",
                new MapSqlParameterSource().addValue("a", answer).addValue("id", id));
    }

    public void saveJudgement(Long id, String result, BigDecimal score, String feedback) {
        jdbc.update("UPDATE t_practice SET judge_result = :r, score = :score, feedback = :fb, "
                        + "judged_at = NOW(3) WHERE id = :id AND judge_result = 'PENDING'",
                new MapSqlParameterSource().addValue("r", result).addValue("score", score)
                        .addValue("fb", feedback).addValue("id", id));
    }

    public Long findKpId(Long id) {
        List<Long> rows = jdbc.queryForList("SELECT kp_id FROM t_practice WHERE id = :id",
                new MapSqlParameterSource("id", id), Long.class);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static PracticeResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PracticeResponse(
                rs.getLong("id"),
                rs.getLong("source_message_id"),
                rs.getLong("session_id"),
                (Long) rs.getObject("kp_id"),
                rs.getString("variant_text"),
                rs.getString("user_answer"),
                rs.getString("judge_result"),
                rs.getBigDecimal("score"),
                rs.getString("feedback"),
                String.valueOf(rs.getTimestamp("created_at")),
                rs.getTimestamp("judged_at") == null ? null : String.valueOf(rs.getTimestamp("judged_at")));
    }
}
