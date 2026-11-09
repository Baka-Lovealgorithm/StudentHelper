package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.LearningStatsResponse.DayCount;
import com.ai.studenthelper.common.dto.LearningStatsResponse.SubjectCount;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * T-305 学情统计聚合。全部为只读查询，数据源是 S1 既有表 + T-203 的知识点命中，
 * 不新建统计表、不做金额换算（FR-206 边界）。
 */
@Repository
public class LearningStatsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public LearningStatsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int count(String sql) {
        Integer value = jdbc.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
        return value == null ? 0 : value;
    }

    public int sessions() {
        return count("SELECT COUNT(*) FROM t_session");
    }

    public int questions() {
        return count("SELECT COUNT(*) FROM t_message WHERE role = 'USER' AND status = 'SUCCESS'");
    }

    public int answers() {
        return count("SELECT COUNT(*) FROM t_message WHERE role = 'ASSISTANT' AND status = 'SUCCESS'");
    }

    public int failedAnswers() {
        return count("SELECT COUNT(*) FROM t_message WHERE status = 'FAILED'");
    }

    public long promptTokens() {
        Long value = jdbc.queryForObject(
                "SELECT IFNULL(SUM(prompt_tokens), 0) FROM t_call_log WHERE status = 'SUCCESS'",
                new MapSqlParameterSource(), Long.class);
        return value == null ? 0L : value;
    }

    public long completionTokens() {
        Long value = jdbc.queryForObject(
                "SELECT IFNULL(SUM(completion_tokens), 0) FROM t_call_log WHERE status = 'SUCCESS'",
                new MapSqlParameterSource(), Long.class);
        return value == null ? 0L : value;
    }

    public int avgDurationMs() {
        Number value = jdbc.queryForObject(
                "SELECT IFNULL(AVG(duration_ms), 0) FROM t_call_log WHERE status = 'SUCCESS'",
                new MapSqlParameterSource(), Number.class);
        return value == null ? 0 : value.intValue();
    }

    /** 最近 7 天按天的提问量，缺少的日期补 0，保证前端条形连续。 */
    public List<DayCount> byDay() {
        List<DayCount> rows = jdbc.query(
                "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS d, COUNT(*) AS c FROM t_message "
                        + "WHERE role = 'USER' AND status = 'SUCCESS' "
                        + "AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) "
                        + "GROUP BY d ORDER BY d ASC",
                new MapSqlParameterSource(),
                (rs, i) -> new DayCount(rs.getString("d"), rs.getInt("c")));
        return rows;
    }

    /** 学科分布：按知识点命中数聚合；未开启知识点抽取时为空列表（不伪造数据）。 */
    public List<SubjectCount> bySubject() {
        return jdbc.query(
                "SELECT kp.subject AS s, COUNT(DISTINCT h.message_id) AS c FROM t_knowledge_hit h "
                        + "JOIN t_knowledge_point kp ON kp.id = h.kp_id "
                        + "GROUP BY kp.subject ORDER BY c DESC, s ASC",
                new MapSqlParameterSource(),
                (rs, i) -> new SubjectCount(rs.getString("s"), rs.getInt("c")));
    }
}
