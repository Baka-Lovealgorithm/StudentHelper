package com.ai.studenthelper;

import com.ai.studenthelper.application.FeedbackApplicationService;
import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.application.SessionApplicationService;
import com.ai.studenthelper.application.UsageApplicationService;
import com.ai.studenthelper.common.dto.FeedbackRequest;
import com.ai.studenthelper.common.dto.UsageSummaryResponse;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.infra.persistence.MessageRepository;
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
 * S2 首批任务的真实 DB 集成验证（T-206 / T-207）。
 * 需要 DB_PASSWORD 环境变量（只经环境变量传递）；整个测试方法在事务内执行并回滚，测试数据不残留。
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class S2FeedbackUsageIntegrationTest {

    @Autowired private S2FeatureService featureService;
    @Autowired private UsageApplicationService usageService;
    @Autowired private FeedbackApplicationService feedbackService;
    @Autowired private SessionApplicationService sessionService;
    @Autowired private MessageRepository messageRepository;
    @Autowired private JdbcTemplate jdbc;

    /** 开关默认关闭（DD-01），集成用例在事务内临时打开，回滚后不影响真实配置。 */
    @BeforeEach
    void enableRequiredFeatures() {
        featureService.update("usagePanel", true);
        featureService.update("feedback", true);
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

        List<UsageSummaryResponse> rows = usageService.summary();
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
    void feedbackMarkIsIdempotentAndCascadesOnMessageDelete() {
        Message message = new Message();
        message.setSessionId(999001L);
        message.setRole("ASSISTANT");
        message.setContent("IT-反馈测试解答");
        message.setGuideMode(0);
        message.setStatus("SUCCESS");
        message.setCreatedAt(LocalDateTime.now());
        Message saved = messageRepository.save(message);

        var first = feedbackService.mark(new FeedbackRequest(saved.getId(), "INCORRECT", "数据结构", "答案有误"));
        var second = feedbackService.mark(new FeedbackRequest(saved.getId(), "INCORRECT", null, null));

        assertEquals(first.id(), second.id(), "重复标记不得插入新行（AC-202-4）");
        assertEquals(1, countMarks(saved.getId()));

        List<?> bySession = feedbackService.list(999001L, null);
        assertEquals(1, bySession.size(), "可按会话检索标记（AC-207-1）");

        sessionService.deleteMessage(saved.getId());
        assertEquals(0, countMarks(saved.getId()), "删除消息应级联清理标记（AC-207-4）");

        // 会话级级联
        Message another = new Message();
        another.setSessionId(999002L);
        another.setRole("ASSISTANT");
        another.setContent("IT-级联测试");
        another.setGuideMode(0);
        another.setStatus("SUCCESS");
        another.setCreatedAt(LocalDateTime.now());
        Message saved2 = messageRepository.save(another);
        feedbackService.mark(new FeedbackRequest(saved2.getId(), "WRONG", "数据结构", null));
        assertTrue(countBySession(999002L) > 0);

        jdbc.update("DELETE FROM t_message_feedback WHERE session_id = ?", 999002L);
        assertEquals(0, countBySession(999002L));
    }

    private int countMarks(Long messageId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_message_feedback WHERE message_id = ?", Integer.class, messageId);
        return n == null ? 0 : n;
    }

    private int countBySession(Long sessionId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_message_feedback WHERE session_id = ?", Integer.class, sessionId);
        return n == null ? 0 : n;
    }
}
