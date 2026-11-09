package com.ai.studenthelper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** 模型配置实体（t_model_config），api_key_cipher 只存密文。 */
@Table("t_model_config")
public class ModelConfig {

    @Id
    private Long id;
    private String name;
    private String provider;
    @Column("base_url")
    private String baseUrl;
    @Column("api_key_cipher")
    private String apiKeyCipher;
    @Column("model_name")
    private String modelName;
    @Column("vision_supported")
    private Integer visionSupported;
    @Column("stream_supported")
    private Integer streamSupported;
    @Column("extra_options")
    private String extraOptions;
    @Column("is_current")
    private Integer isCurrent;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKeyCipher() { return apiKeyCipher; }
    public void setApiKeyCipher(String apiKeyCipher) { this.apiKeyCipher = apiKeyCipher; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Integer getVisionSupported() { return visionSupported; }
    public void setVisionSupported(Integer visionSupported) { this.visionSupported = visionSupported; }
    public Integer getStreamSupported() { return streamSupported; }
    public void setStreamSupported(Integer streamSupported) { this.streamSupported = streamSupported; }
    public String getExtraOptions() { return extraOptions; }
    public void setExtraOptions(String extraOptions) { this.extraOptions = extraOptions; }
    public Integer getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Integer isCurrent) { this.isCurrent = isCurrent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
