package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.UsageSummaryResponse;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 用量看板聚合查询（T-206）。复用 S1 的 t_call_log，不建新表、不暴露原始日志明细。
 * 只提供聚合结果，符合 AC-111-3「调用日志不对外提供明细查询」的边界。
 */
@Repository
public class UsageSummaryRepository {

    private static final String AGGREGATE_SQL = """
            SELECT COALESCE(model_name, '(未记录)')                AS model_name,
                   COUNT(*)                                       AS calls,
                   SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_calls,
                   SUM(CASE WHEN status = 'SUCCESS' THEN 0 ELSE 1 END) AS failed_calls,
                   COALESCE(SUM(prompt_tokens), 0)                AS prompt_tokens,
                   COALESCE(SUM(completion_tokens), 0)            AS completion_tokens,
                   COALESCE(ROUND(AVG(duration_ms)), 0)           AS avg_duration_ms
            FROM t_call_log
            GROUP BY COALESCE(model_name, '(未记录)')
            ORDER BY calls DESC, model_name ASC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public UsageSummaryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UsageSummaryResponse> aggregate() {
        return jdbc.query(AGGREGATE_SQL, Map.of(), (rs, rowNum) -> new UsageSummaryResponse(
                rs.getString("model_name"),
                rs.getLong("calls"),
                rs.getLong("success_calls"),
                rs.getLong("failed_calls"),
                rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens"),
                rs.getLong("avg_duration_ms")));
    }
}
