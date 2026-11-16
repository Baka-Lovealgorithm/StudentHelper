package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.LearningStatsResponse.DayStat;
import com.ai.studenthelper.common.dto.LearningStatsResponse.SubjectCount;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T-305 学情统计聚合。全部为只读查询，数据源是 S1 既有表 + T-203 的知识点命中，
 * 不新建统计表、不做金额换算（FR-206 边界）。
 *
 * <p>CR-023：所有查询按同一窗口过滤（{@code days} = 0 表示全部，1/3/7 表示最近 N 个自然日含今天），
 * 保证「概览计数 = 按天柱状图各行之和」可对账；token 与耗时不再参与学情统计（只留用量看板）。</p>
 */
@Repository
public class LearningStatsRepository {

    /** 「全部」时按天图的展示上限，避免日期无限增长把图压扁。 */
    private static final int ALL_DAYS_LIMIT = 30;

    private final NamedParameterJdbcTemplate jdbc;

    public LearningStatsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int sessions(Integer days) {
        return count("SELECT COUNT(*) FROM t_session" + filter(days, "created_at", false), days);
    }

    public int questions(Integer days) {
        return count("SELECT COUNT(*) FROM t_message WHERE role = 'USER' AND status = 'SUCCESS'"
                + filter(days, "created_at", true), days);
    }

    public int answers(Integer days) {
        return count("SELECT COUNT(*) FROM t_message WHERE role = 'ASSISTANT' AND status = 'SUCCESS'"
                + filter(days, "created_at", true), days);
    }

    public int failedAnswers(Integer days) {
        return count("SELECT COUNT(*) FROM t_message WHERE status = 'FAILED'"
                + filter(days, "created_at", true), days);
    }

    /**
     * 按天三指标（提问 / 成功解答 / 失败轮次）。
     * 窗口为 1/3/7 时**逐日补齐**缺失日期（计 0），保证柱状图 X 轴连续；
     * 窗口为 0（全部）时只返回有数据的日期，最多最近 {@value #ALL_DAYS_LIMIT} 天。
     */
    public List<DayStat> byDay(Integer days) {
        String sql = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS d,"
                + " SUM(CASE WHEN role = 'USER' AND status = 'SUCCESS' THEN 1 ELSE 0 END) AS q,"
                + " SUM(CASE WHEN role = 'ASSISTANT' AND status = 'SUCCESS' THEN 1 ELSE 0 END) AS a,"
                + " SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS f"
                + " FROM t_message"
                + filter(days, "created_at", false)
                + " GROUP BY d ORDER BY d ASC";
        List<DayStat> rows = jdbc.query(sql, params(days),
                (rs, i) -> new DayStat(rs.getString("d"), rs.getInt("q"), rs.getInt("a"), rs.getInt("f")));

        if (!hasWindow(days)) {
            if (rows.size() <= ALL_DAYS_LIMIT) {
                return rows;
            }
            return new ArrayList<>(rows.subList(rows.size() - ALL_DAYS_LIMIT, rows.size()));
        }

        Map<String, DayStat> indexed = new LinkedHashMap<>();
        for (DayStat row : rows) {
            indexed.put(row.date(), row);
        }
        List<DayStat> filled = new ArrayList<>(days);
        LocalDate today = LocalDate.now();
        for (int offset = days - 1; offset >= 0; offset--) {
            String key = today.minusDays(offset).toString();
            DayStat hit = indexed.get(key);
            filled.add(hit == null ? new DayStat(key, 0, 0, 0) : hit);
        }
        return filled;
    }

    /** 学科分布：按知识点命中数聚合；未开启知识点抽取时为空列表（不伪造数据）。 */
    public List<SubjectCount> bySubject(Integer days) {
        return jdbc.query(
                "SELECT kp.subject AS s, COUNT(DISTINCT h.message_id) AS c FROM t_knowledge_hit h "
                        + "JOIN t_knowledge_point kp ON kp.id = h.kp_id"
                        + filter(days, "h.created_at", false)
                        + " GROUP BY kp.subject ORDER BY c DESC, s ASC",
                params(days),
                (rs, i) -> new SubjectCount(rs.getString("s"), rs.getInt("c")));
    }

    private int count(String sql, Integer days) {
        Integer value = jdbc.queryForObject(sql, params(days), Integer.class);
        return value == null ? 0 : value;
    }

    private static boolean hasWindow(Integer days) {
        return days != null && days > 0;
    }

    /**
     * 追加窗口条件。自然日口径：起点为 `CURDATE() - (N-1)` 的 00:00，与按天分组的日期边界对齐，
     * 所以「概览计数」与柱状图各行之和必然相等。
     *
     * @param column      时间列，JOIN 场景需带表别名（如 {@code h.created_at}）
     * @param hasWhereSql 原 SQL 是否已带 WHERE
     */
    private static String filter(Integer days, String column, boolean hasWhereSql) {
        if (!hasWindow(days)) {
            return "";
        }
        return (hasWhereSql ? " AND " : " WHERE ") + column + " >= :since";
    }

    private static MapSqlParameterSource params(Integer days) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (hasWindow(days)) {
            LocalDate start = LocalDate.now().minusDays(days - 1L);
            params.addValue("since", Timestamp.valueOf(start.atStartOfDay()));
        }
        return params;
    }
}
