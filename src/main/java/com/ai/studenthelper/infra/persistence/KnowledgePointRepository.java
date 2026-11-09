package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.agent.KnowledgeExtractor;
import com.ai.studenthelper.common.dto.KnowledgePointResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * T-203 知识点聚合与命中明细（DD-02 / DD-03）。
 * 不建外键，跨表只记 id；hit_count 由明细行数重算，保证 AC-203-1「与实际命中数一致」。
 */
@Repository
public class KnowledgePointRepository {

    private static final String SUBJECT = "数据结构";

    private final NamedParameterJdbcTemplate jdbc;

    public KnowledgePointRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 一次解答命中多个知识点；同一消息对同一知识点只记一条明细（幂等）。 */
    public void recordHits(Long sessionId, Long messageId, List<KnowledgeExtractor.KpRef> refs) {
        for (KnowledgeExtractor.KpRef ref : refs) {
            String major = ref.major() == null ? "" : ref.major().trim();
            String minor = ref.minor() == null ? "" : ref.minor().trim();
            String parentName = major;
            String name = minor.isEmpty() ? major : minor;
            if (name.isEmpty()) {
                continue;
            }
            MapSqlParameterSource p = new MapSqlParameterSource()
                    .addValue("subject", SUBJECT)
                    .addValue("parent", cut(parentName, 128))
                    .addValue("name", cut(name, 128))
                    .addValue("msg", messageId);

            jdbc.update("INSERT INTO t_knowledge_point "
                            + "(subject, parent_name, name, hit_count, mastery, last_message_id, created_at, updated_at) "
                            + "VALUES (:subject, :parent, :name, 0, NULL, :msg, NOW(3), NOW(3)) "
                            + "ON DUPLICATE KEY UPDATE last_message_id = VALUES(last_message_id), updated_at = NOW(3)",
                    p);

            Long kpId = jdbc.queryForObject("SELECT id FROM t_knowledge_point "
                            + "WHERE subject = :subject AND parent_name = :parent AND name = :name",
                    p, Long.class);
            if (kpId == null) {
                continue;
            }

            MapSqlParameterSource hit = new MapSqlParameterSource()
                    .addValue("kp", kpId).addValue("msg", messageId).addValue("session", sessionId);
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM t_knowledge_hit "
                    + "WHERE kp_id = :kp AND message_id = :msg", hit, Integer.class);
            if (exists == null || exists == 0) {
                jdbc.update("INSERT INTO t_knowledge_hit (kp_id, message_id, session_id, created_at) "
                        + "VALUES (:kp, :msg, :session, NOW(3))", hit);
            }
            jdbc.update("UPDATE t_knowledge_point SET hit_count = "
                    + "(SELECT COUNT(*) FROM t_knowledge_hit WHERE kp_id = :kp) WHERE id = :kp", hit);
        }
    }

    public List<KnowledgePointResponse> list(String subject) {
        String filter = (subject == null || subject.isBlank()) ? null : subject.trim();
        MapSqlParameterSource p = new MapSqlParameterSource("filter", filter);
        return jdbc.query("SELECT id, subject, parent_name, name, hit_count, mastery "
                        + "FROM t_knowledge_point "
                        + "WHERE (:filter IS NULL OR subject = :filter) "
                        + "ORDER BY parent_name, name",
                p, (rs, i) -> {
                    BigDecimal mastery = rs.getBigDecimal("mastery");
                    return new KnowledgePointResponse(
                            rs.getLong("id"),
                            rs.getString("subject"),
                            rs.getString("parent_name"),
                            rs.getString("name"),
                            rs.getInt("hit_count"),
                            mastery,
                            mastery == null ? "未评估"
                                    : mastery.multiply(BigDecimal.valueOf(100))
                                    .setScale(0, RoundingMode.HALF_UP) + "%");
                });
    }

    /** DD-03：mastery = PASS/(PASS+FAIL)，有效样本 < 3 时保持 NULL。 */
    public void refreshMastery(Long kpId) {
        if (kpId == null) {
            return;
        }
        MapSqlParameterSource p = new MapSqlParameterSource("kp", kpId);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM t_practice "
                + "WHERE kp_id = :kp AND judge_result IN ('PASS','FAIL')", p, Integer.class);
        if (total == null || total < 3) {
            jdbc.update("UPDATE t_knowledge_point SET mastery = NULL, updated_at = NOW(3) WHERE id = :kp", p);
            return;
        }
        Integer passed = jdbc.queryForObject("SELECT COUNT(*) FROM t_practice "
                + "WHERE kp_id = :kp AND judge_result = 'PASS'", p, Integer.class);
        BigDecimal mastery = BigDecimal.valueOf(passed == null ? 0 : passed)
                .divide(BigDecimal.valueOf(total), 3, RoundingMode.HALF_UP);
        jdbc.update("UPDATE t_knowledge_point SET mastery = :mastery, updated_at = NOW(3) WHERE id = :kp",
                new MapSqlParameterSource().addValue("kp", kpId).addValue("mastery", mastery));
    }

    /** 删除整个会话时批量清理命中明细（AC-203-1 一致性）。 */
    public void removeSessionHits(Long sessionId) {
        MapSqlParameterSource p = new MapSqlParameterSource("session", sessionId);
        List<Long> kpIds = jdbc.queryForList(
                "SELECT DISTINCT kp_id FROM t_knowledge_hit WHERE session_id = :session", p, Long.class);
        jdbc.update("DELETE FROM t_knowledge_hit WHERE session_id = :session", p);
        recount(kpIds);
    }

    /** 删除单条消息时同步清理命中明细并重算 hit_count（AC-203-1 一致性）。 */
    public void removeMessageHits(Long messageId) {
        MapSqlParameterSource p = new MapSqlParameterSource("msg", messageId);
        List<Long> kpIds = jdbc.queryForList(
                "SELECT DISTINCT kp_id FROM t_knowledge_hit WHERE message_id = :msg", p, Long.class);
        jdbc.update("DELETE FROM t_knowledge_hit WHERE message_id = :msg", p);
        recount(kpIds);
    }

    private void recount(List<Long> kpIds) {
        for (Long kpId : kpIds) {
            jdbc.update("UPDATE t_knowledge_point SET hit_count = "
                    + "(SELECT COUNT(*) FROM t_knowledge_hit WHERE kp_id = :kp) WHERE id = :kp",
                    new MapSqlParameterSource("kp", kpId));
        }
    }

    private static String cut(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
