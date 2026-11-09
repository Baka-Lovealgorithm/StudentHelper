package com.ai.studenthelper.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** 上传校验：只认文件头，伪装文件 / 截断文件一律拒绝（AC-101-3、NFR-108）。 */
class ImageFileValidatorTest {

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    }

    @Test
    void acceptsPngSignature() {
        assertEquals("image/png", ImageFileValidator.detectMime(png()));
        assertEquals("png", ImageFileValidator.extensionFor("image/png"));
    }

    @Test
    void acceptsJpegSignature() {
        assertEquals("image/jpeg", ImageFileValidator.detectMime(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}));
        assertEquals("jpg", ImageFileValidator.extensionFor("image/jpeg"));
    }

    @Test
    void acceptsWebpSignature() {
        byte[] webp = "RIFF0000WEBP0000".getBytes(StandardCharsets.US_ASCII);
        assertEquals("image/webp", ImageFileValidator.detectMime(webp));
        assertEquals("webp", ImageFileValidator.extensionFor("image/webp"));
    }

    @Test
    void rejectsExecutableDisguisedAsPng() {
        byte[] exe = "MZ\u0090\u0000\u0003java-byte-code".getBytes(StandardCharsets.ISO_8859_1);
        assertNull(ImageFileValidator.detectMime(exe));
    }

    @Test
    void rejectsTextEvenIfClientClaimsImage() {
        // 校验器根本不接收客户端声明的 Content-Type，声明为 image/png 的文本仍被拒
        assertNull(ImageFileValidator.detectMime("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsTruncatedPng() {
        assertNull(ImageFileValidator.detectMime(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertNull(ImageFileValidator.detectMime(new byte[0]));
        assertNull(ImageFileValidator.detectMime(null));
    }

    @Test
    void extensionIsWhitelisted() {
        assertThrows(IllegalArgumentException.class, () -> ImageFileValidator.extensionFor("../../evil"));
    }
}
