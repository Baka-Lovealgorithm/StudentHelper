package com.ai.studenthelper.infra.persistence;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CR-012 通用厂商配置 / 模型条目持久化。
 * 与 SkillRepository 一致，使用 NamedParameterJdbcTemplate + GeneratedKeyHolder，不引入新实体映射。
 */
@Repository
public class ModelProviderRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plainJdbc;

    public ModelProviderRepository(NamedParameterJdbcTemplate jdbc, JdbcTemplate plainJdbc) {
        this.jdbc = jdbc;
        this.plainJdbc = plainJdbc;
    }

    public record ProviderRow(Long id, String name, String baseUrl, String apiKeyCipher,
                              LocalDateTime updatedAt) {
    }

    public record ModelRow(Long id, Long providerId, String modelName, boolean visionSupported,
                           boolean streamSupported, String extraOptions, boolean isDefault) {
    }

    /** 解题链路需要的完整快照（模型 + 所属厂商）。 */
    public record ResolvedRow(Long modelId, String baseUrl, String apiKeyCipher, String modelName,
                              boolean visionSupported, boolean streamSupported, String extraOptions) {
    }

    /** S1 旧表 t_model_config 的迁移源行。 */
    public record LegacyRow(Long id, String name, String baseUrl, String apiKeyCipher, String modelName,
                            int visionSupported, int streamSupported, String extraOptions, int isCurrent) {
    }

    public List<ProviderRow> findProviders() {
        return jdbc.query("SELECT id, name, base_url, api_key_cipher, updated_at FROM t_model_provider ORDER BY id ASC",
                new MapSqlParameterSource(), (rs, i) -> mapProvider(rs));
    }

    public List<ModelRow> findModels() {
        return jdbc.query("SELECT id, provider_id, model_name, vision_supported, stream_supported, "
                        + "extra_options, is_default FROM t_provider_model ORDER BY id ASC",
                new MapSqlParameterSource(), (rs, i) -> mapModel(rs));
    }

    public Optional<ProviderRow> findProvider(Long id) {
        return jdbc.query("SELECT id, name, base_url, api_key_cipher, updated_at FROM t_model_provider WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> mapProvider(rs)).stream().findFirst();
    }

    public Optional<ModelRow> findModel(Long id) {
        return jdbc.query("SELECT id, provider_id, model_name, vision_supported, stream_supported, "
                        + "extra_options, is_default FROM t_provider_model WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> mapModel(rs)).stream().findFirst();
    }

    public Long insertProvider(String name, String baseUrl, String apiKeyCipher) {
        KeyHolder holder = new GeneratedKeyHolder();
        plainJdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO t_model_provider (name, base_url, api_key_cipher, created_at, updated_at) "
                            + "VALUES (?, ?, ?, NOW(3), NOW(3))", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, baseUrl);
            ps.setString(3, apiKeyCipher);
            return ps;
        }, holder);
        return holder.getKey() == null ? null : holder.getKey().longValue();
    }

    public void updateProvider(Long id, String name, String baseUrl, String apiKeyCipher) {
        jdbc.update("UPDATE t_model_provider SET name = :name, base_url = :baseUrl, "
                        + "api_key_cipher = :cipher, updated_at = NOW(3) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name)
                        .addValue("baseUrl", baseUrl).addValue("cipher", apiKeyCipher));
    }

    public void deleteModelsByProvider(Long providerId) {
        jdbc.update("DELETE FROM t_provider_model WHERE provider_id = :pid",
                new MapSqlParameterSource("pid", providerId));
    }

    public void deleteProvider(Long id) {
        jdbc.update("DELETE FROM t_model_provider WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    public Long insertModel(Long providerId, String modelName, boolean visionSupported,
                            boolean streamSupported, String extraOptions, boolean isDefault) {
        KeyHolder holder = new GeneratedKeyHolder();
        plainJdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO t_provider_model (provider_id, model_name, vision_supported, "
                            + "stream_supported, extra_options, is_default, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, NOW(3), NOW(3))", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, providerId);
            ps.setString(2, modelName);
            ps.setInt(3, visionSupported ? 1 : 0);
            ps.setInt(4, streamSupported ? 1 : 0);
            ps.setString(5, extraOptions);
            ps.setInt(6, isDefault ? 1 : 0);
            return ps;
        }, holder);
        return holder.getKey() == null ? null : holder.getKey().longValue();
    }

    public void updateModel(Long id, String modelName, boolean visionSupported,
                            boolean streamSupported, String extraOptions) {
        jdbc.update("UPDATE t_provider_model SET model_name = :name, vision_supported = :vision, "
                        + "stream_supported = :stream, extra_options = :extra, updated_at = NOW(3) WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("name", modelName)
                        .addValue("vision", visionSupported ? 1 : 0)
                        .addValue("stream", streamSupported ? 1 : 0)
                        .addValue("extra", extraOptions));
    }

    public void deleteModel(Long id) {
        jdbc.update("DELETE FROM t_provider_model WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    public void clearDefault() {
        jdbc.update("UPDATE t_provider_model SET is_default = 0 WHERE is_default = 1",
                new MapSqlParameterSource());
    }

    public void setDefault(Long id) {
        jdbc.update("UPDATE t_provider_model SET is_default = 1, updated_at = NOW(3) WHERE id = :id",
                new MapSqlParameterSource("id", id));
    }

    public Long findDefaultModelId() {
        return jdbc.query("SELECT id FROM t_provider_model WHERE is_default = 1 ORDER BY id ASC LIMIT 1",
                new MapSqlParameterSource(), (rs, i) -> rs.getLong("id")).stream().findFirst().orElse(null);
    }

    public boolean providerNameExists(String name, Long excludeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_model_provider WHERE name = :name"
                        + (excludeId == null ? "" : " AND id <> :exclude"),
                new MapSqlParameterSource().addValue("name", name).addValue("exclude", excludeId), Integer.class);
        return count != null && count > 0;
    }

    public boolean modelNameExists(Long providerId, String modelName, Long excludeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM t_provider_model "
                        + "WHERE provider_id = :pid AND model_name = :name"
                        + (excludeId == null ? "" : " AND id <> :exclude"),
                new MapSqlParameterSource().addValue("pid", providerId).addValue("name", modelName)
                        .addValue("exclude", excludeId), Integer.class);
        return count != null && count > 0;
    }

    public Optional<ResolvedRow> resolve(Long modelId) {
        return jdbc.query("SELECT m.id AS model_id, p.base_url, p.api_key_cipher, m.model_name, "
                        + "m.vision_supported, m.stream_supported, m.extra_options "
                        + "FROM t_provider_model m JOIN t_model_provider p ON p.id = m.provider_id "
                        + "WHERE m.id = :id",
                new MapSqlParameterSource("id", modelId),
                (rs, i) -> new ResolvedRow(rs.getLong("model_id"), rs.getString("base_url"),
                        rs.getString("api_key_cipher"), rs.getString("model_name"),
                        rs.getInt("vision_supported") == 1, rs.getInt("stream_supported") == 1,
                        rs.getString("extra_options"))).stream().findFirst();
    }

    public Long findSessionModelId(Long sessionId) {
        return jdbc.query("SELECT model_id FROM t_session WHERE id = :id",
                new MapSqlParameterSource("id", sessionId),
                (rs, i) -> (Long) rs.getObject("model_id")).stream().findFirst().orElse(null);
    }

    /** 删除模型 / 厂商时把引用它的会话置空，运行时回退全局默认。 */
    public void clearSessionModel(Long modelId) {
        jdbc.update("UPDATE t_session SET model_id = NULL WHERE model_id = :mid",
                new MapSqlParameterSource("mid", modelId));
    }

    public long countProviders() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM t_model_provider",
                new MapSqlParameterSource(), Long.class);
        return count == null ? 0L : count;
    }

    public List<LegacyRow> findLegacyConfigs() {
        return plainJdbc.query("SELECT id, name, base_url, api_key_cipher, model_name, vision_supported, "
                        + "stream_supported, extra_options, is_current FROM t_model_config ORDER BY id ASC",
                (rs, i) -> new LegacyRow(rs.getLong("id"), rs.getString("name"), rs.getString("base_url"),
                        rs.getString("api_key_cipher"), rs.getString("model_name"),
                        rs.getInt("vision_supported"), rs.getInt("stream_supported"),
                        rs.getString("extra_options"), rs.getInt("is_current")));
    }

    /** 迁移收尾：历史会话未选模型时回填全局默认。 */
    public void backfillSessions(Long defaultModelId) {
        if (defaultModelId == null) {
            return;
        }
        jdbc.update("UPDATE t_session SET model_id = :mid WHERE model_id IS NULL",
                new MapSqlParameterSource("mid", defaultModelId));
    }

    private static ProviderRow mapProvider(ResultSet rs) throws SQLException {
        Timestamp updated = rs.getTimestamp("updated_at");
        return new ProviderRow(rs.getLong("id"), rs.getString("name"), rs.getString("base_url"),
                rs.getString("api_key_cipher"), updated == null ? null : updated.toLocalDateTime());
    }

    private static ModelRow mapModel(ResultSet rs) throws SQLException {
        return new ModelRow(rs.getLong("id"), rs.getLong("provider_id"), rs.getString("model_name"),
                rs.getInt("vision_supported") == 1, rs.getInt("stream_supported") == 1,
                rs.getString("extra_options"), rs.getInt("is_default") == 1);
    }
}
