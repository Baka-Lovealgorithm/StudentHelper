package com.ai.studenthelper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** 调用日志实体（t_call_log），不对外提供查询接口（AC-111-3）。 */
@Table("t_call_log")
public class CallLog {

    @Id
    private Long id;
    @Column("message_id")
    private Long messageId;
    @Column("model_config_id")
    private Long modelConfigId;
    @Column("model_name")
    private String modelName;
    @Column("prompt_tokens")
    private Integer promptTokens;
    @Column("completion_tokens")
    private Integer completionTokens;
    /** CR-065：网关回传的缓存命中 prompt token；不透传时为 0。 */
    @Column("cache_hit_tokens")
    private Integer cacheHitTokens;
    /** CR-065：prompt − hit，缓存未命中部分。 */
    @Column("cache_miss_tokens")
    private Integer cacheMissTokens;
    @Column("duration_ms")
    private Integer durationMs;
    private String status;
    @Column("is_debug")
    private Integer isDebug;
    @Column("error_summary")
    private String errorSummary;
    /** CR-018：命中的学科 code 与理由（成功轮也写，便于审计模型自选结果）。 */
    @Column("subject_hit")
    private String subjectHit;
    @Column("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Long getModelConfigId() { return modelConfigId; }
    public void setModelConfigId(Long modelConfigId) { this.modelConfigId = modelConfigId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Integer getCacheHitTokens() { return cacheHitTokens; }
    public void setCacheHitTokens(Integer cacheHitTokens) { this.cacheHitTokens = cacheHitTokens; }
    public Integer getCacheMissTokens() { return cacheMissTokens; }
    public void setCacheMissTokens(Integer cacheMissTokens) { this.cacheMissTokens = cacheMissTokens; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getIsDebug() { return isDebug; }
    public void setIsDebug(Integer isDebug) { this.isDebug = isDebug; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
    public String getSubjectHit() { return subjectHit; }
    public void setSubjectHit(String subjectHit) { this.subjectHit = subjectHit; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
