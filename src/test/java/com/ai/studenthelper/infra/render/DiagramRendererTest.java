package com.ai.studenthelper.infra.render;

import com.ai.studenthelper.config.AppProperties;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-047：渲染器的纯逻辑部分——源码白名单、模板转义、白边裁剪。
 * 不启动浏览器：真实渲染由手工冒烟验证，避免测试机没有 Chrome 时误红。
 */
class DiagramRendererTest {

    private static DiagramRenderer renderer() {
        AppProperties properties = new AppProperties();
        properties.getDiagram().setWorkDir(System.getProperty("java.io.tmpdir") + "/sh-diagram-test");
        properties.getDiagram().setMaxCodeChars(120);
        return new DiagramRenderer(properties);
    }

    @Test
    void acceptsSupportedDiagramHeads() {
        DiagramRenderer renderer = renderer();
        assertNull(renderer.validate("flowchart TD\n  A[\"1\"] --> B[\"2\"]"));
        assertNull(renderer.validate("graph LR\n  A --> B"));
        assertNull(renderer.validate("stateDiagram-v2\n  [*] --> S1"));
        assertNull(renderer.validate("  flowchart   LR\n A-->B  "));
    }

    @Test
    void rejectsEmptyLongUnsafeAndUnknownHeads() {
        DiagramRenderer renderer = renderer();
        assertEquals("empty", renderer.validate(null));
        assertEquals("empty", renderer.validate("   "));
        assertEquals("too-long", renderer.validate("flowchart TD\n" + "A".repeat(200)));
        assertEquals("unsafe-content", renderer.validate("flowchart TD\n  A[\"<script>alert(1)</script>\"]"));
        assertEquals("unsafe-content", renderer.validate("flowchart TD\n  click A javascript:alert(1)"));
        assertEquals("unsupported-diagram", renderer.validate("hello world"));
    }

    @Test
    void escapesSourceIntoJsonInsteadOfHtml() {
        DiagramRenderer renderer = renderer();
        String code = "flowchart TD\n  A[\"a\\\"b</script>\"] --> B[\"2\"]";
        String html = renderer.buildHtml(code);
        // 源码出现在 JSON 载荷里，且 </script> 被转义，不会提前闭合 script 标签
        assertTrue(html.contains("application/json"));
        assertFalse(html.contains("</script>\"]"), "未转义的 </script> 会截断模板");
        assertTrue(html.contains("<\\/script>"));
        // 载荷中的换行被转义为 \n，避免 HTML 内出现裸换行以外的结构
        assertTrue(html.contains("flowchart TD\\n"));
        assertNotNull(DiagramRenderer.toJson("x"));
    }

    @Test
    void jsonSerializesQuotesAndControlChars() {
        assertEquals("\"a\\\"b\"", DiagramRenderer.toJson("a\"b"));
        assertEquals("\"a\\\\b\"", DiagramRenderer.toJson("a\\b"));
        assertEquals("\"a\\nb\"", DiagramRenderer.toJson("a\nb"));
        assertEquals("\"a\\u0001b\"", DiagramRenderer.toJson("a\u0001b"));
    }

    @Test
    void cropTrimsWhiteBorderAndKeepsContent() throws Exception {
        byte[] source = png(240, 160, 60, 40, 80, 50);
        var cropped = renderer().crop(source);
        assertTrue(cropped.isPresent());
        assertTrue(cropped.get().width() < 240, "宽度应小于原图");
        assertTrue(cropped.get().height() < 160, "高度应小于原图");
        assertTrue(cropped.get().width() >= 80, "内容块不能被裁掉");
        assertTrue(cropped.get().png().length > 0);
    }

    @Test
    void cropRejectsFullyWhiteImage() throws Exception {
        byte[] blank = png(200, 120, -1, -1, 0, 0);
        assertTrue(renderer().crop(blank).isEmpty(), "整张纯白视为渲染失败");
    }

    /** 生成一张白底 PNG；contentW ≤ 0 时整张留白。 */
    private static byte[] png(int width, int height, int x, int y, int contentW, int contentH)
            throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            if (contentW > 0 && contentH > 0) {
                g.setColor(new Color(53, 88, 212));
                g.fillRect(x, y, contentW, contentH);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
