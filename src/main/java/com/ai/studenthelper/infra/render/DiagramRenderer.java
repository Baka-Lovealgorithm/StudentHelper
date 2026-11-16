package com.ai.studenthelper.infra.render;

import com.ai.studenthelper.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * CR-047：把 Mermaid 源码渲染成 PNG（供 {@code diagram_render} 工具落盘后给前端展示）。
 *
 * <p>渲染方式：本机 Chromium 内核浏览器（Chrome / Edge）无头模式加载一个本地 HTML，
 * 页面里用 `mermaid.min.js` 画出 SVG，再 `--screenshot` 截图为 PNG，最后由 Java 裁掉白边。
 * 刻意不引入 mermaid-cli / puppeteer：那会把 Node 与上百 MB Chromium 拖进运行时，
 * 而本项目的图只需要静态 PNG，浏览器进程一次性调用即可。</p>
 *
 * <p>安全：Mermaid 源码经 JSON 序列化后注入 `textContent`（不做字符串拼 HTML），
 * `securityLevel: 'strict'`，长度与首行关键字另有白名单校验；渲染进程 15s 超时。</p>
 */
@Component
public class DiagramRenderer {

    private static final Logger log = LoggerFactory.getLogger(DiagramRenderer.class);
    private static final String RUNTIME_JS = "mermaid.min.js";
    /** 允许的图类型首行关键字（宽松白名单，真正的语法校验交给 Mermaid）。 */
    private static final List<String> ALLOWED_HEADS = List.of(
            "flowchart", "graph", "statediagram", "classdiagram", "sequencediagram",
            "erdiagram", "journey", "mindmap", "timeline", "pie", "xychart");
    private static final int PADDING = 12;

    private final AppProperties.Diagram config;
    private final Path workRoot;
    private volatile String chromeExecutable;

    public DiagramRenderer(AppProperties properties) {
        this.config = properties.getDiagram();
        this.workRoot = Path.of(config.getWorkDir()).toAbsolutePath().normalize();
    }

    /** 渲染结果：PNG 字节与像素尺寸。 */
    public record Rendered(byte[] png, int width, int height) {
    }

    /** 浏览器是否可用（工具据此决定注册与否）。 */
    public boolean available() {
        return resolveBrowser() != null;
    }

    /**
     * 渲染一张图。任何异常都转成 {@code Optional.empty()}，由工具层降级为文字提示，
     * 绝不因为画图失败打断解题主链路。
     */
    public Optional<Rendered> render(String code) {
        String problem = validate(code);
        if (problem != null) {
            log.info("DIAGRAM_REJECT reason={}", problem);
            return Optional.empty();
        }
        String browser = resolveBrowser();
        if (browser == null) {
            log.warn("DIAGRAM_SKIP reason=browser-not-found");
            return Optional.empty();
        }
        Path workDir = workRoot.resolve("work");
        Path html = workDir.resolve("d-" + Long.toHexString(System.nanoTime()) + ".html");
        Path png = workDir.resolve("d-" + Long.toHexString(System.nanoTime()) + ".png");
        try {
            Files.createDirectories(workDir);
            ensureRuntime();
            Files.writeString(html, buildHtml(code), StandardCharsets.UTF_8);
            if (!screenshot(browser, html, png)) {
                return Optional.empty();
            }
            byte[] raw = Files.readAllBytes(png);
            return crop(raw);
        } catch (IOException | RuntimeException e) {
            log.warn("DIAGRAM_RENDER_FAILED reason={}", e.toString());
            return Optional.empty();
        } finally {
            deleteQuietly(html);
            deleteQuietly(png);
        }
    }

    /** 白名单校验；返回 null 表示通过。 */
    String validate(String code) {
        if (code == null || code.isBlank()) {
            return "empty";
        }
        String trimmed = code.strip();
        if (trimmed.length() > config.getMaxCodeChars()) {
            return "too-long";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("<script") || lower.contains("<iframe") || lower.contains("javascript:")) {
            return "unsafe-content";
        }
        String head = lower.split("\\s+", 2)[0];
        return ALLOWED_HEADS.stream().anyMatch(head::startsWith) ? null : "unsupported-diagram";
    }

    /** 生成渲染页：源码走 JSON + textContent，杜绝 HTML / JS 注入。 */
    String buildHtml(String code) {
        String payload = toJson(code).replace("</", "<\\/");
        return """
                <!doctype html><html><head><meta charset="utf-8">
                <style>
                  html,body{margin:0;padding:0;background:#fff}
                  #stage{display:inline-block;padding:%dpx}
                  #payload{display:none}
                  #target{margin:0;background:transparent}
                </style>
                <script src="../%s"></script>
                </head><body>
                <script id="payload" type="application/json">%s</script>
                <div id="stage"><pre class="mermaid" id="target"></pre></div>
                <script>
                  (function () {
                    var code = JSON.parse(document.getElementById('payload').textContent);
                    var node = document.getElementById('target');
                    node.textContent = code;
                    window.mermaid.initialize({
                      startOnLoad: false,
                      securityLevel: 'strict',
                      theme: 'base',
                      fontFamily: 'system-ui, -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif',
                      themeVariables: {
                        primaryColor: '#eef2ff', primaryBorderColor: '#3558d4',
                        primaryTextColor: '#1f2430', lineColor: '#6b7280',
                        secondaryColor: '#f6f7fb', tertiaryColor: '#ffffff',
                        background: '#ffffff', fontSize: '15px'
                      },
                      flowchart: { useMaxWidth: false, htmlLabels: false },
                      sequence: { useMaxWidth: false },
                      class: { useMaxWidth: false },
                      state: { useMaxWidth: false },
                      er: { useMaxWidth: false }
                    });
                    window.mermaid.run({ nodes: [node] }).catch(function (e) {
                      document.title = 'RENDER_ERROR';
                      var box = document.createElement('pre');
                      box.id = 'render-error';
                      box.textContent = 'RENDER_ERROR';
                      document.body.appendChild(box);
                    });
                  })();
                </script>
                </body></html>
                """.formatted(PADDING * 2, RUNTIME_JS, payload);
    }

    /** 极简 JSON 字符串序列化：只需要转义引号、反斜杠与控制字符。 */
    static String toJson(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private boolean screenshot(String browser, Path html, Path png) {
        List<String> command = new ArrayList<>();
        command.add(browser);
        command.add("--headless=new");
        command.add("--disable-gpu");
        command.add("--no-sandbox");
        command.add("--hide-scrollbars");
        command.add("--force-device-scale-factor=" + config.getScale());
        command.add("--virtual-time-budget=" + config.getVirtualTimeBudgetMs());
        command.add("--window-size=" + config.getWindowWidth() + "," + config.getWindowHeight());
        command.add("--screenshot=" + png.toAbsolutePath());
        command.add(html.toUri().toString());
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            // 先等进程结束再读输出：Chrome 无头模式的日志只有几行，不会写满管道；
            // 反过来先 transferTo 会一直阻塞到进程退出，超时控制就失效了。
            if (!process.waitFor(config.getTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                log.warn("DIAGRAM_TIMEOUT ms={}", config.getTimeoutMs());
                return false;
            }
            try (InputStream in = process.getInputStream()) {
                in.transferTo(OutputStreamSink.INSTANCE);
            }
            return process.exitValue() == 0 && Files.isRegularFile(png) && Files.size(png) > 256;
        } catch (IOException e) {
            log.warn("DIAGRAM_SPAWN_FAILED reason={}", e.toString());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** 读 PNG 并裁掉四周纯白，留固定边距；整张全白视为渲染失败。 */
    Optional<Rendered> crop(byte[] raw) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(raw));
            if (image == null) {
                return Optional.empty();
            }
            int minX = image.getWidth();
            int minY = image.getHeight();
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (!isWhite(image.getRGB(x, y))) {
                        if (x < minX) minX = x;
                        if (y < minY) minY = y;
                        if (x > maxX) maxX = x;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            if (maxX < 0 || maxY < 0) {
                return Optional.empty();
            }
            int width = maxX - minX + 1;
            int height = maxY - minY + 1;
            int pad = PADDING * config.getScale() / 2;
            BufferedImage out = new BufferedImage(width + pad * 2, height + pad * 2, BufferedImage.TYPE_INT_RGB);
            var g = out.createGraphics();
            try {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, out.getWidth(), out.getHeight());
                g.drawImage(image.getSubimage(minX, minY, width, height), pad, pad, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return Optional.of(new Rendered(bytes.toByteArray(), out.getWidth(), out.getHeight()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static boolean isWhite(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return r >= 248 && g >= 248 && b >= 248;
    }

    /** 首次渲染时把 classpath 里的 mermaid.min.js 落到工作目录，后续复用。 */
    private void ensureRuntime() throws IOException {
        Path target = workRoot.resolve(RUNTIME_JS);
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            return;
        }
        try (InputStream in = getClass().getResourceAsStream("/diagram/" + RUNTIME_JS)) {
            if (in == null) {
                throw new IOException("classpath:/diagram/" + RUNTIME_JS + " 缺失");
            }
            Files.createDirectories(workRoot);
            Files.write(target, in.readAllBytes());
        }
    }

    /** 配置优先；否则按顺序探测 Chrome / Edge，找不到返回 null。 */
    private String resolveBrowser() {
        String cached = chromeExecutable;
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        synchronized (this) {
            if (chromeExecutable != null) {
                return chromeExecutable.isEmpty() ? null : chromeExecutable;
            }
            List<String> candidates = new ArrayList<>();
            if (config.getChromePath() != null && !config.getChromePath().isBlank()) {
                candidates.add(config.getChromePath());
            }
            String localAppData = System.getenv("LOCALAPPDATA");
            String programFiles = System.getenv("ProgramFiles");
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            for (String base : List.of(programFiles, programFilesX86)) {
                if (base != null) {
                    candidates.add(base + "\\Google\\Chrome\\Application\\chrome.exe");
                    candidates.add(base + "\\Microsoft\\Edge\\Application\\msedge.exe");
                }
            }
            if (localAppData != null) {
                candidates.add(localAppData + "\\Google\\Chrome\\Application\\chrome.exe");
            }
            candidates.add("/usr/bin/google-chrome");
            candidates.add("/usr/bin/chromium");
            for (String candidate : candidates) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                if (Files.isRegularFile(Path.of(candidate))) {
                    chromeExecutable = candidate;
                    log.info("DIAGRAM_BROWSER path={}", candidate);
                    return candidate;
                }
            }
            chromeExecutable = "";
            log.warn("DIAGRAM_BROWSER_NOT_FOUND checked={}", candidates.size());
            return null;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件清理失败不影响结果
        }
    }

    /** 丢掉浏览器输出流的黑洞。 */
    private static final class OutputStreamSink extends java.io.OutputStream {
        static final OutputStreamSink INSTANCE = new OutputStreamSink();

        @Override
        public void write(int b) {
            // discard
        }

        @Override
        public void write(byte[] b, int off, int len) {
            // discard
        }
    }
}
