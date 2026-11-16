package com.ai.studenthelper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** CR-037：历史图片入上下文与尺寸压缩参数；全部有默认值，配置不覆盖时即按裁决口径。 */
@Component
@ConfigurationProperties(prefix = "studenthelper.image-context")
public class ImageContextProperties {

    /** 图片最长边上限（px），超过才等比缩小。 */
    private int maxEdge = 1536;

    /** 缩放后重编码的 JPEG 质量。 */
    private double jpegQuality = 0.85;

    /** 保留原图的「最近带图 USER 消息」轮数。 */
    private int recentImageMessages = 2;

    /** 一次上下文里历史图片总数上限。 */
    private int maxHistoryImages = 4;

    /** 单张图片在压缩触发判定里的 token 估算额度（非计费口径）。 */
    private int tokenPerImage = 765;

    public int getMaxEdge() { return maxEdge; }
    public void setMaxEdge(int maxEdge) { this.maxEdge = maxEdge; }
    public double getJpegQuality() { return jpegQuality; }
    public void setJpegQuality(double jpegQuality) { this.jpegQuality = jpegQuality; }
    public int getRecentImageMessages() { return recentImageMessages; }
    public void setRecentImageMessages(int recentImageMessages) { this.recentImageMessages = recentImageMessages; }
    public int getMaxHistoryImages() { return maxHistoryImages; }
    public void setMaxHistoryImages(int maxHistoryImages) { this.maxHistoryImages = maxHistoryImages; }
    public int getTokenPerImage() { return tokenPerImage; }
    public void setTokenPerImage(int tokenPerImage) { this.tokenPerImage = tokenPerImage; }
}
