package com.ai.studenthelper.infra.storage;

import com.ai.studenthelper.config.ImageContextProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * CR-037 / AC-504-3：图片在「发给模型前」统一做尺寸压缩，结果不落盘。
 *
 * <p>仅当最长边超过 {@code maxEdge} 才等比缩小，透明像素合成白底后重编码 JPEG；
 * 低于阈值、WebP（JDK ImageIO 无解码器）、解码或缩放失败一律原样返回，绝不阻断解题。</p>
 */
@Component
public class ImageOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);

    /** 压缩结果；reduced=false 表示字节与 MIME 与输入完全一致。 */
    public record Optimized(byte[] bytes, String mimeType, String extension, boolean reduced) {
        public static Optimized original(byte[] bytes, String mimeType, String extension) {
            return new Optimized(bytes, mimeType, extension, false);
        }
    }

    private final ImageContextProperties properties;

    public ImageOptimizer(ImageContextProperties properties) {
        this.properties = properties;
    }

    public Optimized optimize(byte[] bytes, String mimeType) {
        String extension = extensionOf(mimeType);
        if (bytes == null || bytes.length == 0) {
            return Optimized.original(bytes, mimeType, extension);
        }
        // JDK 内置 ImageIO 只支持 png / jpeg；webp 等保持原图
        if (!"png".equals(extension) && !"jpg".equals(extension)) {
            return Optimized.original(bytes, mimeType, extension);
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) {
                return Optimized.original(bytes, mimeType, extension);
            }
            int width = source.getWidth();
            int height = source.getHeight();
            int maxEdge = Math.max(1, properties.getMaxEdge());
            int longest = Math.max(width, height);
            if (longest <= maxEdge) {
                return Optimized.original(bytes, mimeType, extension);
            }
            double scale = (double) maxEdge / longest;
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = scaled.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }
            double quality = Math.min(1.0, Math.max(0.1, properties.getJpegQuality()));
            byte[] encoded = writeJpeg(scaled, quality);
            // 线条 / 纯色截图的 PNG 可能比缩后的 JPEG 还小；此时保留原图，避免「越压越大」
            if (encoded.length >= bytes.length) {
                log.info("IMAGE_OPTIMIZE_SKIPPED reason=jpeg-not-smaller original={} optimized={}",
                        bytes.length, encoded.length);
                return Optimized.original(bytes, mimeType, extension);
            }
            log.info("IMAGE_OPTIMIZE size={}x{}->{}x{} bytes={}->{}",
                    width, height, targetWidth, targetHeight, bytes.length, encoded.length);
            return new Optimized(encoded, "image/jpeg", "jpg", true);
        } catch (Exception e) {
            log.warn("IMAGE_OPTIMIZE_FAILED reason={}", e.toString());
            return Optimized.original(bytes, mimeType, extension);
        }
    }

    private static byte[] writeJpeg(BufferedImage image, double quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality((float) quality);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /** MIME → 落盘扩展名（与 ImageFileValidator 口径一致，额外容忍历史文件的 gif）。 */
    public static String extensionOf(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "";
        };
    }

    /** 历史文件按扩展名还原 MIME；未知返回 null，调用方按原样降级。 */
    public static String mimeOfFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return null;
    }
}
