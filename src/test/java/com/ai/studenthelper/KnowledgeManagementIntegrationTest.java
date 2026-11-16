package com.ai.studenthelper;

import com.ai.studenthelper.agent.KnowledgeExtractor;
import com.ai.studenthelper.application.KnowledgeApplicationService;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.KnowledgePageResponse;
import com.ai.studenthelper.common.dto.KnowledgePointResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CR-040 / AC-507-1~3 / AC-507-6：知识点管理的真实数据库行为。
 *
 * <p>日期边界、级联删除、命中次数与明细一致性都无法用 mock 证明，必须打到 MySQL。
 * 整个类在事务内执行并回滚，开关改动与测试数据都不会残留。</p>
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class KnowledgeManagementIntegrationTest {

    /** 独立学科命名空间，避免与开发数据/其它用例互相干扰。 */
    private static final String SUBJECT = "IT-CR040-知识点";

    @Autowired private KnowledgeApplicationService service;
    @Autowired private KnowledgeExtractor knowledgeExtractor;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void ensureFeatureOn() {
        jdbc.update("UPDATE t_feature_toggle SET enabled = 1 WHERE feature_key = 'knowledgeExtract'");
    }

    private void touchCreatedAt(Long id, LocalDate day, int hour) {
        jdbc.update("UPDATE t_knowledge_point SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(day.atTime(LocalTime.of(hour, 0))), id);
    }

    /** AC-507-1：创建日期闭区间、可只填一端、单日区间，且与学科筛选叠加。 */
    @Test
    void listFiltersByCreatedDateRange() {
        LocalDate today = LocalDate.now();
        Long ancient = service.create(SUBJECT, "栈", "十天前的条目").id();
        Long yesterday = service.create(SUBJECT, "队列", "昨天的条目").id();
        Long todayKp = service.create(SUBJECT, "树", "今天的条目").id();
        Long other = service.create(SUBJECT + "-另一个", "树", "其它学科条目").id();
        touchCreatedAt(ancient, today.minusDays(10), 9);
        touchCreatedAt(yesterday, today.minusDays(1), 9);
        touchCreatedAt(todayKp, today, 1);
        touchCreatedAt(other, today, 2);

        assertEquals(3, service.list(SUBJECT, null, null).size(), "不限日期取该学科全部");
        List<KnowledgePointResponse> singleDay = service.list(SUBJECT, today.toString(), today.toString());
        assertEquals(1, singleDay.size(), "单日区间只含当天");
        assertEquals(0, singleDay.get(0).hitCount(), "手动新增命中次数为 0");
        assertNotNull(singleDay.get(0).createdAt(), "响应带 createdAt 供核对日期");
        assertEquals(2, service.list(SUBJECT, today.minusDays(10).toString(),
                today.minusDays(1).toString()).size(), "闭区间含两端");
        assertEquals(2, service.list(SUBJECT, today.minusDays(1).toString(), null).size(), "只填起始");
        assertEquals(1, service.list(SUBJECT, null, today.minusDays(10).toString()).size(), "只填结束");
    }

    /** AC-507-1：非法日期与倒置区间在用例层拒绝（真实库不参与）。 */
    @Test
    void invalidDateRangeIsRejected() {
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.list(SUBJECT, "2026-02-30", null)).getErrorCode());
        assertEquals(ErrorCode.E1004, assertThrows(BusinessException.class,
                () -> service.list(SUBJECT, "2026-11-13", "2026-11-12")).getErrorCode());
    }

    /** AC-507-2：新增落库、命中次数 0、不写命中明细、重复拒绝且不覆盖。 */
    @Test
    void manualCreateIsIdempotentOnUniqueKey() {
        KnowledgePointResponse created = service.create(SUBJECT, "栈", "手动条目");
        assertEquals(0, created.hitCount());
        assertEquals(SUBJECT, created.subject());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_knowledge_hit WHERE kp_id = ?", Integer.class, created.id()),
                "手动新增不写命中明细");

        jdbc.update("UPDATE t_knowledge_point SET hit_count = 5 WHERE id = ?", created.id());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(SUBJECT, "栈", "手动条目"));
        assertEquals(ErrorCode.E1004, error.getErrorCode());
        assertEquals(5, jdbc.queryForObject(
                "SELECT hit_count FROM t_knowledge_point WHERE id = ?", Integer.class, created.id()),
                "重复新增不得覆盖原记录的命中次数");
    }

    /** AC-507-3：删除级联清理命中明细；不存在的 id 返回 E1005。 */
    @Test
    void deleteCascadesHits() {
        Long id = service.create(SUBJECT, "图", "待删条目").id();
        jdbc.update("INSERT INTO t_knowledge_hit (kp_id, message_id, session_id, created_at) "
                + "VALUES (?, 990001, 880001, NOW(3)), (?, 990002, 880001, NOW(3))", id, id);
        jdbc.update("UPDATE t_knowledge_point SET hit_count = 2 WHERE id = ?", id);

        service.delete(id);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_knowledge_point WHERE id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_knowledge_hit WHERE kp_id = ?", Integer.class, id),
                "命中明细必须级联删除");
        assertEquals(ErrorCode.E1005, assertThrows(BusinessException.class,
                () -> service.delete(id)).getErrorCode());
    }

    /** AC-507-7：每页 10 条，末页 hasMore=false，越界页返回空列表但 total 正确。 */
    @Test
    void paginationReturnsTenPerPage() {
        for (int i = 1; i <= 12; i++) {
            service.create(SUBJECT, "分页", "条目" + i);
        }
        KnowledgePageResponse first = service.page(SUBJECT, null, null, 0, 10);
        assertEquals(10, first.items().size());
        assertEquals(12L, first.total());
        assertTrue(first.hasMore());

        KnowledgePageResponse second = service.page(SUBJECT, null, null, 1, 10);
        assertEquals(2, second.items().size());
        assertEquals(12L, second.total());
        assertFalse(second.hasMore(), "末页必须没有下一页");

        KnowledgePageResponse beyond = service.page(SUBJECT, null, null, 5, 10);
        assertEquals(0, beyond.items().size());
        assertEquals(12L, beyond.total());
        assertFalse(beyond.hasMore());

        // 页内条目不得重复（ORDER BY parent_name, name 稳定序）
        List<Long> ids = new java.util.ArrayList<>();
        first.items().forEach(row -> ids.add(row.id()));
        second.items().forEach(row -> ids.add(row.id()));
        assertEquals(12, new java.util.HashSet<>(ids).size(), "两页合计应恰好覆盖 12 条且无重复");
    }

    /** AC-507-6：自动抽取链路未回归——第 2 段解析后命中次数与明细仍逐行一致。 */
    @Test
    void extractionStillKeepsHitCountConsistent() {
        knowledgeExtractor.extractAndRecord(880002L, 990003L, SUBJECT,
                "栈 > 栈顶指针\n队列 > 循环队列\n这不是知识点这一行没有分隔符而且特别长长长长长长长长长长");

        List<KnowledgePointResponse> rows = service.list(SUBJECT, null, null);
        assertEquals(2, rows.size(), "每轮最多 3 条，且超长 major 被丢弃");
        for (KnowledgePointResponse row : rows) {
            assertEquals(1, row.hitCount());
            assertEquals(row.hitCount(), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_knowledge_hit WHERE kp_id = ?", Integer.class, row.id()),
                    "hit_count 必须与明细行数一致");
        }
    }

    /** AC-507-6：开关关闭时四个接口口径统一拒绝。 */
    @Test
    void closedFeatureRejectsAllOperations() {
        jdbc.update("UPDATE t_feature_toggle SET enabled = 0 WHERE feature_key = 'knowledgeExtract'");
        assertEquals(ErrorCode.E3004, assertThrows(BusinessException.class,
                () -> service.list(null, null, null)).getErrorCode());
        assertEquals(ErrorCode.E3004, assertThrows(BusinessException.class,
                () -> service.subjects()).getErrorCode());
        assertEquals(ErrorCode.E3004, assertThrows(BusinessException.class,
                () -> service.create(SUBJECT, null, "关闭态条目")).getErrorCode());
        assertEquals(ErrorCode.E3004, assertThrows(BusinessException.class,
                () -> service.delete(1L)).getErrorCode());
    }
}
