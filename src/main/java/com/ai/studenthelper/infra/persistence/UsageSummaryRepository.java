package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.UsageDailyResponse;
import com.ai.studenthelper.common.dto.UsageSummaryResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用量看板聚合查询（T-206 + CR-014）。复用 S1 的 t_call_log，不建新表、不暴露原始日志明细。
 * 聚合恒带 {@code is_debug = 0}，调试 / 桩服务行只在库里留审计、不计入看板（AC-206-6）。
 * days 为 1 / 3 / 7 时按 created_at 的滚动时间窗口过滤；0 或 null 表示全部（AC-206-5）。
 * CR-071：未命中按 {@code prompt − 命中} 推导，保证聚合后 {@code hit + miss = prompt}，
 * 命中率因此恒等于「命中 ÷ Prompt token」；升级脚本之前写入的历史行两列为 0，按 0% 命中计入。
 */
@Repository
public class UsageSummaryRepository {

    /**
     * CR-071：两条 SQL 都把未命中写成同一段推导式——`SUM(GREATEST(prompt − hit, 0))`。
     * 直接内联而非拼常量，避免 text block 拼接带来的缩进 / 换行歧义，便于逐行核对。
     */
    private static final String AGGREGATE_SQL = """
            SELECT COALESCE(model_name, '(未记录)')                AS model_name,
                   COUNT(*)                                       AS calls,
                   SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_calls,
                   SUM(CASE WHEN status = 'SUCCESS' THEN 0 ELSE 1 END) AS failed_calls,
                   COALESCE(SUM(prompt_tokens), 0)                AS prompt_tokens,
                   COALESCE(SUM(completion_tokens), 0)            AS completion_tokens,
                   COALESCE(SUM(cache_hit_tokens), 0)             AS cache_hit_tokens,
                   COALESCE(SUM(GREATEST(COALESCE(prompt_tokens, 0)
                       - COALESCE(cache_hit_tokens, 0), 0)), 0)   AS cache_miss_tokens,
                   COALESCE(ROUND(AVG(duration_ms)), 0)           AS avg_duration_ms
            FROM t_call_log
            WHERE is_debug = 0
            """;

    private static final String DAILY_SQL = """
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d')            AS day,
                   COUNT(*)                                       AS calls,
                   SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_calls,
                   SUM(CASE WHEN status = 'SUCCESS' THEN 0 ELSE 1 END) AS failed_calls,
                   COALESCE(SUM(prompt_tokens), 0)                AS prompt_tokens,
                   COALESCE(SUM(completion_tokens), 0)            AS completion_tokens,
                   COALESCE(SUM(cache_hit_tokens), 0)             AS cache_hit_tokens,
                   COALESCE(SUM(GREATEST(COALESCE(prompt_tokens, 0)
                       - COALESCE(cache_hit_tokens, 0), 0)), 0)   AS cache_miss_tokens
            FROM t_call_log
            WHERE is_debug = 0
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public UsageSummaryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 按模型聚合；days <= 0 表示不过滤时间。 */
    public List<UsageSummaryResponse> aggregate(Integer days) {
        return jdbc.query(sqlWithWindow(AGGREGATE_SQL, days, "GROUP BY COALESCE(model_name, '(未记录)') "
                        + "ORDER BY calls DESC, model_name ASC"),
                params(days), (rs, rowNum) -> new UsageSummaryResponse(
                        rs.getString("model_name"),
                        rs.getLong("calls"),
                        rs.getLong("success_calls"),
                        rs.getLong("failed_calls"),
                        rs.getLong("prompt_tokens"),
                        rs.getLong("completion_tokens"),
                        rs.getLong("avg_duration_ms"),
                        rs.getLong("cache_hit_tokens"),
                        rs.getLong("cache_miss_tokens")));
    }

    /** 按天分桶；与聚合共用同一过滤条件，保证两处数字可对账。 */
    public List<UsageDailyResponse> daily(Integer days) {
        return jdbc.query(sqlWithWindow(DAILY_SQL, days,
                        "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d') ORDER BY day ASC"),
                params(days), (rs, rowNum) -> new UsageDailyResponse(
                        rs.getString("day"),
                        rs.getLong("calls"),
                        rs.getLong("success_calls"),
                        rs.getLong("failed_calls"),
                        rs.getLong("prompt_tokens"),
                        rs.getLong("completion_tokens"),
                        rs.getLong("cache_hit_tokens"),
                        rs.getLong("cache_miss_tokens")));
    }

    private static boolean hasWindow(Integer days) {
        return days != null && days > 0;
    }

    private static String sqlWithWindow(String base, Integer days, String tail) {
        return base + (hasWindow(days) ? " AND created_at >= :since" : "") + " " + tail;
    }

    private static MapSqlParameterSource params(Integer days) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (hasWindow(days)) {
            params.addValue("since", Timestamp.valueOf(LocalDateTime.now().minusDays(days)));
        }
        return params;
    }
}
