package com.ai.studenthelper.infra.persistence;

import com.ai.studenthelper.common.dto.SubjectSkillResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** CR-018：学科规范持久化；与通用技能 t_skill 完全独立。 */
@Repository
public class SubjectSkillRepository {

    private static final String COLUMNS = "id, code, name, description, scope, enabled, keywords, "
            + "spec_md, sections_json, spec_json, created_at, updated_at";

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;

    public SubjectSkillRepository(NamedParameterJdbcTemplate jdbc, JdbcTemplate plainJdbc) {
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
    }

    public List<SubjectSkillResponse> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM t_subject_skill "
                        + "ORDER BY enabled DESC, id ASC",
                new MapSqlParameterSource(), (rs, i) -> map(rs));
    }

    /** 停用后的条目不出现在 AUTO 清单（AC-501-1）。 */
    public List<SubjectSkillResponse> findEnabled() {
        return jdbc.query("SELECT " + COLUMNS + " FROM t_subject_skill WHERE enabled = 1 "
                        + "ORDER BY id ASC",
                new MapSqlParameterSource(), (rs, i) -> map(rs));
    }

    public Optional<SubjectSkillResponse> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        List<SubjectSkillResponse> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM t_subject_skill WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> map(rs));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<SubjectSkillResponse> findEnabledByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        List<SubjectSkillResponse> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM t_subject_skill WHERE code = :code AND enabled = 1",
                new MapSqlParameterSource("code", code.trim()), (rs, i) -> map(rs));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public boolean nameExists(String name, Long excludeId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_subject_skill WHERE name = :name"
                        + (excludeId == null ? "" : " AND id <> :exclude"),
                new MapSqlParameterSource().addValue("name", name).addValue("exclude", excludeId),
                Integer.class);
        return count != null && count > 0;
    }

    public Long insert(String code, String name, String description, String scope, boolean enabled,
                       String keywords, String specMd, String sectionsJson, String specJson) {
        KeyHolder holder = new GeneratedKeyHolder();
        plainJdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO t_subject_skill (code, name, description, scope, enabled, keywords, "
                            + "spec_md, sections_json, spec_json, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(3), NOW(3))",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setString(4, scope);
            ps.setInt(5, enabled ? 1 : 0);
            ps.setString(6, keywords);
            ps.setString(7, specMd);
            ps.setString(8, sectionsJson);
            ps.setString(9, specJson);
            return ps;
        }, holder);
        return holder.getKey() == null ? null : holder.getKey().longValue();
    }

    public void update(Long id, String name, String description, String keywords,
                       String specMd, String sectionsJson, String specJson, Boolean enabled) {
        jdbc.update("UPDATE t_subject_skill SET name = :name, description = :description, "
                        + "keywords = :keywords, spec_md = :specMd, sections_json = :sectionsJson, "
                        + "spec_json = :specJson, "
                        + "enabled = COALESCE(:enabled, enabled), updated_at = NOW(3) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name)
                        .addValue("description", description).addValue("keywords", keywords)
                        .addValue("specMd", specMd).addValue("sectionsJson", sectionsJson)
                        .addValue("specJson", specJson)
                        .addValue("enabled", enabled == null ? null : (enabled ? 1 : 0)));
    }

    /** 内置行也允许停用；单独启停不触碰规范正文。 */
    public void setEnabled(Long id, boolean enabled) {
        jdbc.update("UPDATE t_subject_skill SET enabled = :enabled, updated_at = NOW(3) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("enabled", enabled ? 1 : 0));
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM t_subject_skill WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    /** 工具式自选：subject_get 被调用时把命中学科固化到会话，流式生成之前即完成。 */
    public void bindToSession(Long sessionId, Long skillId) {
        if (sessionId == null || skillId == null) {
            return;
        }
        jdbc.update("UPDATE t_session SET subject_skill_id = :skillId WHERE id = :sessionId "
                        + "AND subject_skill_id IS NULL",
                new MapSqlParameterSource().addValue("sessionId", sessionId)
                        .addValue("skillId", skillId));
    }

    /** 删除自定义学科时清掉会话引用，避免悬空 id。 */
    public void unbindSessions(Long skillId) {
        jdbc.update("UPDATE t_session SET subject_skill_id = NULL WHERE subject_skill_id = :skillId",
                new MapSqlParameterSource("skillId", skillId));
    }

    private static SubjectSkillResponse map(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new SubjectSkillResponse(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("description"), rs.getString("scope"), rs.getInt("enabled") == 1,
                rs.getString("keywords"), rs.getString("spec_md"), rs.getString("sections_json"),
                created == null ? null : created.toLocalDateTime(),
                updated == null ? null : updated.toLocalDateTime(),
                rs.getString("spec_json"));
    }
}
