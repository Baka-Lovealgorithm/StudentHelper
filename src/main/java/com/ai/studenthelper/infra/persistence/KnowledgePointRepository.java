package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.agent.KnowledgeExtractor;
import com.ai.studenthelper.common.dto.KnowledgePointResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * T-203 知识点聚合与命中明细（DD-02 / DD-03）；CR-040 起支持手动新增、按创建日期区间筛选与删除。
 *
 * <p>不建外键，跨表只记 id；{@code hit_count} 由明细行数重算，保证「与实际命中数一致」。
 * 掌握度（{@code mastery}）按 CR-040 用户裁决退役：列保留在表里，仓储层不再读写。</p>
 */
@Repository
public class KnowledgePointRepository {

    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NamedParameterJdbcTemplate jdbc;

    public KnowledgePointRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 一次解答命中多个知识点；同一消息对同一知识点只记一条明细（幂等）。CR-025：subject 由调用方按会话学科传入。 */
    public void recordHits(Long sessionId, Long messageId, String subject,
                           List<KnowledgeExtractor.KpRef> refs) {
        for (KnowledgeExtractor.KpRef ref : refs) {
            String major = ref.major() == null ? "" : ref.major().trim();
            String minor = ref.minor() == null ? "" : ref.minor().trim();
            String parentName = major;
            String name = minor.isEmpty() ? major : minor;
            if (name.isEmpty()) {
                continue;
            }
            MapSqlParameterSource p = new MapSqlParameterSource()
                    .addValue("subject", subject)
                    .addValue("parent", cut(parentName, 128))
                    .addValue("name", cut(name, 128))
                    .addValue("msg", messageId);

            jdbc.update("INSERT INTO t_knowledge_point "
                            + "(subject, parent_name, name, hit_count, last_message_id, created_at, updated_at) "
                            + "VALUES (:subject, :parent, :name, 0, :msg, NOW(3), NOW(3)) "
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

    /**
     * CR-040：列表。三个条件同时生效（AND），日期区间作用在 {@code created_at} 上，
     * {@code to} 按「含当天整天」处理（{@code < to + 1 天}），两端均可为 {@code null}。
     */
    public List<KnowledgePointResponse> list(String subject, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT id, subject, parent_name, name, hit_count, created_at "
                        + "FROM t_knowledge_point" + whereClause() + " ORDER BY parent_name, name",
                whereParams(subject, from, to), KnowledgePointRepository::mapRow);
    }

    /**
     * CR-040：分页列表。排序与筛选口径与 {@link #list} 完全一致（{@code parent_name, name} 稳定序），
     * 交给「更多」按 OFFSET 追加，因此翻页期间不应插入/删除数据（前端在增删后回到第 0 页）。
     */
    public List<KnowledgePointResponse> listPage(String subject, LocalDate from, LocalDate to,
                                                int limit, long offset) {
        MapSqlParameterSource p = whereParams(subject, from, to)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query("SELECT id, subject, parent_name, name, hit_count, created_at "
                        + "FROM t_knowledge_point" + whereClause()
                        + " ORDER BY parent_name, name LIMIT :limit OFFSET :offset",
                p, KnowledgePointRepository::mapRow);
    }

    /** CR-040：与 {@link #listPage} 同口径的总数，用于 {@code hasMore} 与「共 N 个」展示。 */
    public long count(String subject, LocalDate from, LocalDate to) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_knowledge_point" + whereClause(),
                whereParams(subject, from, to), Long.class);
        return total == null ? 0L : total;
    }

    /** 三条件共用的 WHERE（列表 / 分页 / 计数必须逐字一致，避免总数与页内条数打架）。 */
    private static String whereClause() {
        return " WHERE (:filter IS NULL OR subject = :filter) "
                + "AND (:from IS NULL OR created_at >= :from) "
                + "AND (:to IS NULL OR created_at < DATE_ADD(:to, INTERVAL 1 DAY))";
    }

    private static MapSqlParameterSource whereParams(String subject, LocalDate from, LocalDate to) {
        String filter = (subject == null || subject.isBlank()) ? null : subject.trim();
        return new MapSqlParameterSource()
                .addValue("filter", filter)
                .addValue("from", from, Types.DATE)
                .addValue("to", to, Types.DATE);
    }

    /** CR-040：新增后回读单条，供接口直接返回该条记录。 */
    public Optional<KnowledgePointResponse> findById(Long id) {
        List<KnowledgePointResponse> rows = jdbc.query("SELECT id, subject, parent_name, name, "
                        + "hit_count, created_at FROM t_knowledge_point WHERE id = :id",
                new MapSqlParameterSource("id", id), KnowledgePointRepository::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static KnowledgePointResponse mapRow(ResultSet rs, int index) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        LocalDateTime value = created == null ? null : created.toLocalDateTime();
        return new KnowledgePointResponse(
                rs.getLong("id"),
                rs.getString("subject"),
                rs.getString("parent_name"),
                rs.getString("name"),
                rs.getInt("hit_count"),
                value == null ? null : value.format(CREATED_AT_FORMAT));
    }

    /** CR-025：前端学科下拉数据源，去重后按字典序返回。 */
    public List<String> distinctSubjects() {
        return jdbc.queryForList(
                "SELECT DISTINCT subject FROM t_knowledge_point WHERE subject IS NOT NULL "
                        + "ORDER BY subject", new MapSqlParameterSource(), String.class);
    }

    /** CR-040：手动新增前的重复检查，与自动抽取共用唯一键 {@code (subject, parent_name, name)}。 */
    public boolean exists(String subject, String parentName, String name) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_knowledge_point "
                        + "WHERE subject = :subject AND parent_name = :parent AND name = :name",
                new MapSqlParameterSource().addValue("subject", subject)
                        .addValue("parent", parentName == null ? "" : parentName)
                        .addValue("name", name),
                Integer.class);
        return count != null && count > 0;
    }

    /** CR-040：手动新增。命中次数固定 0、不写命中明细，避免污染「真实命中」统计。 */
    public Long insertManual(String subject, String parentName, String name) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("subject", subject)
                .addValue("parent", parentName == null ? "" : parentName)
                .addValue("name", name);
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO t_knowledge_point "
                + "(subject, parent_name, name, hit_count, created_at, updated_at) "
                + "VALUES (:subject, :parent, :name, 0, NOW(3), NOW(3))", p, holder);
        return holder.getKey() == null ? null : holder.getKey().longValue();
    }

    /** CR-040：删除条目并级联清理命中明细；返回删除的聚合行数（0 表示不存在）。 */
    public int delete(Long id) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("kp", id);
        jdbc.update("DELETE FROM t_knowledge_hit WHERE kp_id = :kp", p);
        return jdbc.update("DELETE FROM t_knowledge_point WHERE id = :kp", p);
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
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
