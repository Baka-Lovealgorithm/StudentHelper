package com.ai.studenthelper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** 会话实体（t_session）。本机单用户，不携带 user_id。 */
@Table("t_session")
public class Session {

    @Id
    private Long id;
    private String title;
    @Column("guide_mode")
    private Integer guideMode;
    /** CR-012：会话自己选用的模型条目 id；NULL 时运行时回退全局默认。 */
    @Column("model_id")
    private Long modelId;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getGuideMode() { return guideMode; }
    public void setGuideMode(Integer guideMode) { this.guideMode = guideMode; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
