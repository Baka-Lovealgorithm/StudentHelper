package com.ai.studenthelper.infra.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 分步解锁记录（T-209）。uk_unlock(message_id, step) 保证重复解锁幂等。 */
@Repository
public class StepUnlockRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public StepUnlockRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void unlock(Long sessionId, Long messageId, int step) {
        jdbc.update("INSERT IGNORE INTO t_step_unlock (session_id, message_id, step, unlocked_at) "
                        + "VALUES (:session, :message, :step, NOW(3))",
                new MapSqlParameterSource().addValue("session", sessionId)
                        .addValue("message", messageId).addValue("step", step));
    }

    /** 未解锁任何一级时返回 1（默认只展示 ①②③）。 */
    public int maxUnlockedStep(Long messageId) {
        Integer value = jdbc.queryForObject(
                "SELECT MAX(step) FROM t_step_unlock WHERE message_id = :message",
                new MapSqlParameterSource("message", messageId), Integer.class);
        return value == null ? 1 : Math.max(1, value);
    }

    public void deleteBySessionId(Long sessionId) {
        jdbc.update("DELETE FROM t_step_unlock WHERE session_id = :session",
                new MapSqlParameterSource("session", sessionId));
    }

    public void deleteByMessageId(Long messageId) {
        jdbc.update("DELETE FROM t_step_unlock WHERE message_id = :message",
                new MapSqlParameterSource("message", messageId));
    }
}
