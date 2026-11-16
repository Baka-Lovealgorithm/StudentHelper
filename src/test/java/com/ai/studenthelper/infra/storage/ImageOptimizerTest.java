package com.ai.studenthelper.infra.storage;

import com.ai.studenthelper.config.ImageContextProperties;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CR-037 / AC-504-3：尺寸压缩与降级口径。 */
class ImageOptimizerTest {

    private final ImageOptimizer optimizer = new ImageOptimizer(new ImageContextProperties());

    /** 纯色 / 线条图，PNG 压缩率极高。 */
    private static byte[] flatPng(int width, int height, boolean withAlpha) throws Exception {
        BufferedImage image = new BufferedImage(width, height,
                withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(withAlpha ? new Color(0, 0, 0, 0) : Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);
        graphics.drawLine(0, 0, width - 1, height - 1);
        graphics.dispose();
        return encodePng(image);
    }

    /** 照片级噪点图，PNG 体积大，缩放 + JPEG 后必然显著变小。 */
    private static byte[] noisyPng(int width, int height, boolean withAlpha) throws Exception {
        BufferedImage image = new BufferedImage(width, height,
                withAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                int rgb = random.nextInt(0xFFFFFF);
                for (int dy = 0; dy < 2 && y + dy < height; dy++) {
                    for (int dx = 0; dx < 2 && x + dx < width; dx++) {
                        image.setRGB(x + dx, y + dy, rgb);
                    }
                }
            }
        }
        return encodePng(image);
    }

    private static byte[] encodePng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void largePngIsDownscaledAndReencodedAsJpeg() throws Exception {
        byte[] source = noisyPng(2000, 1000, false);
        ImageOptimizer.Optimized result = optimizer.optimize(source, "image/png");
        assertTrue(result.reduced(), "超过 1536 的边应触发缩放");
        assertEquals("image/jpeg", result.mimeType());
        assertEquals("jpg", result.extension());
        assertTrue(result.bytes().length < source.length, "压缩后字节应更小");
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()));
        assertEquals(1536, Math.max(decoded.getWidth(), decoded.getHeight()));
        assertEquals(768, Math.min(decoded.getWidth(), decoded.getHeight()));
    }

    @Test
    void smallImageIsReturnedByteForByte() throws Exception {
        byte[] source = flatPng(400, 300, false);
        ImageOptimizer.Optimized result = optimizer.optimize(source, "image/png");
        assertFalse(result.reduced());
        assertArrayEquals(source, result.bytes());
        assertEquals("image/png", result.mimeType());
    }

    @Test
    void transparentPixelsAreFlattenedOnWhite() throws Exception {
        byte[] source = noisyPng(1800, 1200, true);
        ImageOptimizer.Optimized result = optimizer.optimize(source, "image/png");
        assertTrue(result.reduced());
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()));
        int rgb = decoded.getRGB(0, decoded.getHeight() - 1) & 0xFFFFFF;
        assertTrue(rgb > 0xD0D0D0, "透明区域应被合成为白底，实际=" + Integer.toHexString(rgb));
    }

    @Test
    void oversizedJpegResultFallsBackToOriginalPng() throws Exception {
        // 纯色大图：PNG 极小，重编码 JPEG 反而更大，应原样返回
        byte[] source = flatPng(2000, 1000, false);
        ImageOptimizer.Optimized result = optimizer.optimize(source, "image/png");
        assertFalse(result.reduced(), "压完没变小应回退原图");
        assertArrayEquals(source, result.bytes());
        assertEquals("image/png", result.mimeType());
    }

    @Test
    void webpIsLeftUntouched() {
        byte[] source = "fake-webp-bytes".getBytes();
        ImageOptimizer.Optimized result = optimizer.optimize(source, "image/webp");
        assertFalse(result.reduced());
        assertArrayEquals(source, result.bytes());
        assertEquals("image/webp", result.mimeType());
    }

    @Test
    void brokenBytesFallBackToOriginal() {
        byte[] source = "not-an-image".getBytes();
        ImageOptimizer.Optimized result = optimizer.optimize(source, "image/png");
        assertFalse(result.reduced());
        assertArrayEquals(source, result.bytes());
    }

    @Test
    void mimeOfFileNameCoversWhitelist() {
        assertEquals("image/png", ImageOptimizer.mimeOfFileName("a.png"));
        assertEquals("image/jpeg", ImageOptimizer.mimeOfFileName("a.JPG"));
        assertEquals("image/webp", ImageOptimizer.mimeOfFileName("a.webp"));
        assertNull(ImageOptimizer.mimeOfFileName("a.txt"));
    }
}
