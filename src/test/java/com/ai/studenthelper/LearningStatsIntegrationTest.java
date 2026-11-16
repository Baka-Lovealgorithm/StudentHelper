package com.ai.studenthelper;

import com.ai.studenthelper.application.LearningStatsApplicationService;
import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.application.SessionApplicationService;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.LearningStatsResponse;
import com.ai.studenthelper.common.dto.LearningStatsResponse.DayStat;
import com.ai.studenthelper.common.dto.LearningStatsResponse.SubjectCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-023 学情统计窗口化的真实 DB 验证：同一窗口下「概览计数 = 按天柱状图各行之和」，
 * 缺失日期逐日补 0，非法窗口拒绝，学科分布随窗口收敛。
 * 整个测试方法在事务内执行并回滚，测试数据不残留、开关回滚后恢复原值。
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class LearningStatsIntegrationTest {

    @Autowired private S2FeatureService featureService;
    @Autowired private LearningStatsApplicationService statsService;
    @Autowired private SessionApplicationService sessionService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void enableStats() {
        featureService.update("learningStats", true);
    }

    @Test
    void windowFillsMissingDaysAndTotalsMatchChart() {
        Long sessionId = sessionService.create("IT-学情窗口").id();
        LocalDateTime noon = LocalDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0);

        // 今天：2 提问 + 1 成功解答 + 1 失败；前天：1 提问；5 天前：1 提问（只有「全部」能看到）
        insertMessage(sessionId, "USER", "SUCCESS", noon);
        insertMessage(sessionId, "USER", "SUCCESS", noon.minusMinutes(10));
        insertMessage(sessionId, "ASSISTANT", "SUCCESS", noon);
        insertMessage(sessionId, "ASSISTANT", "FAILED", noon);
        insertMessage(sessionId, "USER", "SUCCESS", noon.minusDays(2));
        insertMessage(sessionId, "USER", "SUCCESS", noon.minusDays(5));

        // 最近 3 个自然日（含今天）：起点 = 今天 - 2 天
        LocalDate start3 = LocalDate.now().minusDays(2);
        LearningStatsResponse three = statsService.summary(3);
        assertEquals(3, three.days());
        assertEquals(3, three.byDay().size(), "3 天窗口应逐日补齐 3 格");
        assertEquals(LocalDate.now().toString(), three.byDay().get(2).date());
        assertEquals(start3.toString(), three.byDay().get(0).date());
        assertEquals(countSql("USER", "SUCCESS", start3), three.questions());
        assertEquals(countSql("ASSISTANT", "SUCCESS", start3), three.answers());
        assertEquals(countFailedSql(start3), three.failedAnswers());
        assertEquals(three.questions(), sumQuestions(three.byDay()), "概览提问数应等于柱状图各行之和");
        assertEquals(three.answers(), three.byDay().stream().mapToInt(DayStat::answers).sum());
        assertEquals(three.failedAnswers(), three.byDay().stream().mapToInt(DayStat::failed).sum());
        assertTrue(three.questions() >= 3, "前天与今天的提问都应计入 3 天窗口");
        assertTrue(three.sessions() >= 1);

        // 最近 1 天：只剩今天 2 条提问
        LearningStatsResponse one = statsService.summary(1);
        assertEquals(1, one.byDay().size());
        assertTrue(one.questions() >= 2);
        assertTrue(one.questions() < three.questions(), "1 天窗口应严格小于 3 天窗口");

        // 最近 7 天：包含 5 天前那条
        LearningStatsResponse seven = statsService.summary(7);
        assertEquals(7, seven.byDay().size());
        assertTrue(seven.questions() > three.questions(), "7 天窗口应包含 5 天前的提问");

        // 全部：不过滤时间，按天图最多 30 格
        LearningStatsResponse all = statsService.summary(0);
        assertEquals(0, all.days());
        assertTrue(all.questions() >= seven.questions());
        assertTrue(all.byDay().size() <= 30);
    }

    @Test
    void unsupportedWindowRejectsWithE1004() {
        BusinessException error = assertThrows(BusinessException.class, () -> statsService.summary(2));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
    }

    @Test
    void subjectDistributionFollowsWindow() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO t_knowledge_point (subject, parent_name, name, hit_count, created_at, updated_at)"
                + " VALUES (?,?,?,?,?,?)", "IT-窗口学科", "", "IT-窗口知识点", 2,
                Timestamp.valueOf(now), Timestamp.valueOf(now));
        Long kpId = jdbc.queryForObject(
                "SELECT id FROM t_knowledge_point WHERE subject = ? AND parent_name = '' AND name = ?",
                Long.class, "IT-窗口学科", "IT-窗口知识点");

        // 一条今天的命中 + 一条 20 天前的命中（message_id 不同，避免 COUNT(DISTINCT) 合并）
        insertHit(kpId, 900001L, now);
        insertHit(kpId, 900002L, now.minusDays(20));

        List<SubjectCount> withinOneDay = statsService.summary(1).bySubject();
        List<SubjectCount> allTime = statsService.summary(0).bySubject();

        assertEquals(1, countOf(withinOneDay, "IT-窗口学科"), "1 天窗口只应计入今天的命中");
        assertEquals(2, countOf(allTime, "IT-窗口学科"), "全部窗口应计入两条命中");
    }

    private void insertMessage(Long sessionId, String role, String status, LocalDateTime at) {
        jdbc.update("INSERT INTO t_message (session_id, role, content, guide_mode, status, created_at)"
                + " VALUES (?,?,?,?,?,?)", sessionId, role, "IT-学情统计窗口", 0, status, Timestamp.valueOf(at));
    }

    private void insertHit(Long kpId, Long messageId, LocalDateTime at) {
        jdbc.update("INSERT INTO t_knowledge_hit (kp_id, message_id, session_id, created_at) VALUES (?,?,?,?)",
                kpId, messageId, 1L, Timestamp.valueOf(at));
    }

    private int countSql(String role, String status, LocalDate start) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM t_message WHERE role = ? AND status = ?"
                        + " AND created_at >= ?", Integer.class, role, status,
                Timestamp.valueOf(start.atStartOfDay()));
        return value == null ? 0 : value;
    }

    private int countFailedSql(LocalDate start) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM t_message WHERE status = 'FAILED'"
                + " AND created_at >= ?", Integer.class, Timestamp.valueOf(start.atStartOfDay()));
        return value == null ? 0 : value;
    }

    private static int sumQuestions(List<DayStat> rows) {
        return rows.stream().mapToInt(DayStat::questions).sum();
    }

    private static int countOf(List<SubjectCount> rows, String subject) {
        return rows.stream().filter(row -> subject.equals(row.subject()))
                .mapToInt(SubjectCount::count).findFirst().orElse(0);
    }
}
