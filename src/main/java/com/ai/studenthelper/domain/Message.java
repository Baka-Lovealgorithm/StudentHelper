package com.ai.studenthelper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** 消息实体（t_message），含图片引用、状态与 token 用量。 */
@Table("t_message")
public class Message {

    @Id
    private Long id;
    @Column("session_id")
    private Long sessionId;
    private String role;
    private String content;
    @Column("hint")
    private String hint;
    @Column("image_paths")
    private String imagePaths;
    @Column("model_config_id")
    private Long modelConfigId;
    @Column("guide_mode")
    private Integer guideMode;
    private String status;
    @Column("error_code")
    private String errorCode;
    @Column("prompt_tokens")
    private Integer promptTokens;
    @Column("completion_tokens")
    private Integer completionTokens;
    @Column("duration_ms")
    private Integer durationMs;
    /** CR-018：该回答所用输出结构；NULL 按默认六段，FREE_TEXT 为追问自由文本。 */
    @Column("section_schema")
    private String sectionSchema;
    /** CR-018：该回答采用的学科与结构标签，供前端「本次采用」展示。 */
    @Column("subject_label")
    private String subjectLabel;
    /** CR-039：本轮回答形态（SOLVE / FOLLOW_UP / EXPLAIN）；历史行为 NULL，按 section_schema 推断。 */
    @Column("intent")
    private String intent;
    @Column("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
    public String getImagePaths() { return imagePaths; }
    public void setImagePaths(String imagePaths) { this.imagePaths = imagePaths; }
    public Long getModelConfigId() { return modelConfigId; }
    public void setModelConfigId(Long modelConfigId) { this.modelConfigId = modelConfigId; }
    public Integer getGuideMode() { return guideMode; }
    public void setGuideMode(Integer guideMode) { this.guideMode = guideMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getSectionSchema() { return sectionSchema; }
    public void setSectionSchema(String sectionSchema) { this.sectionSchema = sectionSchema; }
    public String getSubjectLabel() { return subjectLabel; }
    public void setSubjectLabel(String subjectLabel) { this.subjectLabel = subjectLabel; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
