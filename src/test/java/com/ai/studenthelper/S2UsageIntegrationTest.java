package com.ai.studenthelper;

import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.application.UsageApplicationService;
import com.ai.studenthelper.common.dto.UsageDailyResponse;
import com.ai.studenthelper.common.dto.UsageSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2 首批任务的真实 DB 集成验证（T-206 用量看板）。
 *
 * <p>CR-041 退役反馈闭环（T-207）后，本类只保留用量用例；原 {@code S2FeedbackUsageIntegrationTest}
 * 中的反馈标记用例连同 {@code FeedbackApplicationService} 一并删除，
 * 标记级联语义由 {@code SessionApplicationServiceTest} 与错题本用例继续覆盖。</p>
 *
 * <p>需要 DB_PASSWORD 环境变量（只经环境变量传递）；整个测试方法在事务内执行并回滚，测试数据不残留。</p>
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class S2UsageIntegrationTest {

    @Autowired private S2FeatureService featureService;
    @Autowired private UsageApplicationService usageService;
    @Autowired private JdbcTemplate jdbc;

    /** 开关默认关闭（DD-01），集成用例在事务内临时打开，回滚后不影响真实配置。 */
    @BeforeEach
    void enableRequiredFeatures() {
        featureService.update("usagePanel", true);
    }

    @Test
    void usageAggregateMatchesDirectSqlWithoutAmountConversion() {
        String model = "IT-usage-model";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("INSERT INTO t_call_log (model_name, prompt_tokens, completion_tokens, duration_ms, status, created_at) VALUES (?,?,?,?,?,?)",
                model, 100, 200, 1000, "SUCCESS", now);
        jdbc.update("INSERT INTO t_call_log (model_name, prompt_tokens, completion_tokens, duration_ms, status, created_at) VALUES (?,?,?,?,?,?)",
                model, 300, 400, 3000, "SUCCESS", now);
        jdbc.update("INSERT INTO t_call_log (model_name, prompt_tokens, completion_tokens, duration_ms, status, created_at) VALUES (?,?,?,?,?,?)",
                model, 0, 0, 5000, "FAILED", now);

        List<UsageSummaryResponse> rows = usageService.summary(0).rows();
        UsageSummaryResponse row = rows.stream()
                .filter(r -> model.equals(r.modelName()))
                .findFirst().orElseThrow();

        assertEquals(3L, row.calls(), "调用次数应与插入行数一致（AC-206-1）");
        assertEquals(2L, row.successCalls());
        assertEquals(1L, row.failedCalls());
        assertEquals(400L, row.promptTokens(), "token 汇总应与 SQL 直查一致（AC-206-2）");
        assertEquals(600L, row.completionTokens());
        assertEquals(3000L, row.avgDurationMs());
        assertEquals(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM t_call_log WHERE model_name = ?", Long.class, model),
                row.calls());
    }

    @Test
    void debugRowsAreExcludedAndDailyMatchesAggregate() {
        String model = "IT-debug-model";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("INSERT INTO t_call_log "
                        + "(model_name, prompt_tokens, completion_tokens, duration_ms, status, created_at, is_debug) "
                        + "VALUES (?,?,?,?,?,?,1)",
                model, 999, 999, 1000, "SUCCESS", now);

        var all = usageService.summary(0);
        assertTrue(all.rows().stream().noneMatch(r -> model.equals(r.modelName())),
                "is_debug=1 的行不得出现在按模型聚合中（AC-206-6）");

        long rowCalls = all.rows().stream().mapToLong(UsageSummaryResponse::calls).sum();
        long dailyCalls = all.daily().stream().mapToLong(UsageDailyResponse::calls).sum();
        assertEquals(rowCalls, dailyCalls, "按天分桶合计应与按模型聚合合计一致（AC-206-5）");

        var oneDay = usageService.summary(1);
        assertEquals(1, oneDay.days());
        assertTrue(oneDay.rows().stream().mapToLong(UsageSummaryResponse::calls).sum() <= rowCalls,
                "最近 1 天窗口的调用数不得超过全部窗口");
    }

    /**
     * CR-071：未命中按 {@code prompt − 命中} 推导，聚合后必须满足
     * {@code hit + miss = prompt}，命中率因此恒等于「命中 ÷ Prompt token」。
     * 覆盖两种行：带缓存字段的行、升级脚本之前写入（两列默认 0）的历史行。
     */
    @Test
    void cacheMissIsDerivedFromPromptSoHitPlusMissEqualsPrompt() {
        String model = "IT-cache-model";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("INSERT INTO t_call_log "
                        + "(model_name, prompt_tokens, completion_tokens, duration_ms, status, created_at, "
                        + "cache_hit_tokens, cache_miss_tokens) VALUES (?,?,?,?,?,?,?,?)",
                model, 1000, 100, 1000, "SUCCESS", now, 600, 400);
        // 历史行：两列保持默认 0，等价于「没有缓存数据」，按 0% 命中计入
        jdbc.update("INSERT INTO t_call_log "
                        + "(model_name, prompt_tokens, completion_tokens, duration_ms, status, created_at) "
                        + "VALUES (?,?,?,?,?,?)",
                model, 500, 50, 1000, "SUCCESS", now);

        var all = usageService.summary(0);
        UsageSummaryResponse row = all.rows().stream()
                .filter(r -> model.equals(r.modelName()))
                .findFirst().orElseThrow();

        assertEquals(600L, row.cacheHitTokens(), "命中 token 应取 cache_hit_tokens 之和");
        assertEquals(900L, row.cacheMissTokens(), "未命中应按 prompt − 命中 推导（1000−600 + 500−0）");
        assertEquals(row.promptTokens(), row.cacheHitTokens() + row.cacheMissTokens(),
                "CR-071：命中 + 未命中应恒等于 prompt token");

        long rowPrompt = all.rows().stream().mapToLong(UsageSummaryResponse::promptTokens).sum();
        long rowHit = all.rows().stream().mapToLong(UsageSummaryResponse::cacheHitTokens).sum();
        long rowMiss = all.rows().stream().mapToLong(UsageSummaryResponse::cacheMissTokens).sum();
        long dailyPrompt = all.daily().stream().mapToLong(UsageDailyResponse::promptTokens).sum();
        long dailyHit = all.daily().stream().mapToLong(UsageDailyResponse::cacheHitTokens).sum();
        long dailyMiss = all.daily().stream().mapToLong(UsageDailyResponse::cacheMissTokens).sum();
        assertEquals(rowPrompt, rowHit + rowMiss, "按模型聚合应满足 hit + miss = prompt");
        assertEquals(dailyPrompt, dailyHit + dailyMiss, "按天分桶应满足 hit + miss = prompt");
        assertEquals(rowPrompt, dailyPrompt, "按模型与按天的 prompt 合计应一致");
    }
}
