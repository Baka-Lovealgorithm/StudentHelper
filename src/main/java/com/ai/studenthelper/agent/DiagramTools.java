package com.ai.studenthelper.agent;

import com.ai.studenthelper.infra.render.DiagramRenderer;
import com.ai.studenthelper.infra.storage.ImageStorage;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * CR-047：作图工具。后端把 Mermaid 源码渲染成 PNG 落盘，返回 Markdown 图片链接，
 * 模型只需把链接放进答案——答案正文与数据库里都不会出现绘图源码。
 *
 * <p>刻意不做成 Spring Bean：每个请求 new 一个实例并绑定 sessionId，图片落到该会话目录；
 * 每轮最多 2 张、同一份源码重复调用直接复用已有链接。</p>
 */
public class DiagramTools {

    /** 与内置技能正文约定一致：同一题最多 2 张图。 */
    static final int MAX_PER_TURN = 2;

    private final DiagramRenderer renderer;
    private final ImageStorage storage;
    private final Long sessionId;
    private final Consumer<String> onRendered;
    private final Map<String, String> cache = new LinkedHashMap<>();
    private final List<String> links = new ArrayList<>();

    public DiagramTools(DiagramRenderer renderer, ImageStorage storage, Long sessionId) {
        this(renderer, storage, sessionId, null);
    }

    public DiagramTools(DiagramRenderer renderer, ImageStorage storage, Long sessionId,
                        Consumer<String> onRendered) {
        this.renderer = renderer;
        this.storage = storage;
        this.sessionId = sessionId;
        this.onRendered = onRendered;
    }

    /** 本轮已生成的 Markdown 图片链接（按生成顺序），供收尾时兜底补图。 */
    public List<String> links() {
        return List.copyOf(links);
    }

    @Tool(name = "diagram_render",
            description = "把 Mermaid 源码渲染成图片，返回可直接嵌入答案的 Markdown 图片链接。"
                    + "题目涉及树 / 图 / 链表的结构、算法执行步骤、状态迁移或坐标曲线时调用；"
                    + "每轮最多 2 张。返回值里的 ![...](...) 必须原样放进答案对应段落，"
                    + "不要在答案里输出 Mermaid 源码、代码块或图片路径以外的描述。",
            readOnly = false,
            concurrencySafe = false)
    public String diagramRender(
            @ToolParam(name = "code", required = true,
                    description = "Mermaid 源码，例如 flowchart TD 开头；节点标签含空格/括号/冒号时用双引号包裹")
            String code,
            @ToolParam(name = "alt", required = false,
                    description = "图片替代文字，简短说明这张图画的是什么")
            String alt) {
        String source = code == null ? "" : code.strip();
        if (source.isEmpty()) {
            return "绘图失败：code 为空。请改用文字描述结构，不要输出代码块。";
        }
        String cached = cache.get(source);
        if (cached != null) {
            return "该图已生成，直接引用：" + cached + "（不要重复绘图）。";
        }
        if (links.size() >= MAX_PER_TURN) {
            return "本轮绘图已达上限（" + MAX_PER_TURN + " 张）。请直接引用已生成的图片链接，"
                    + "其余结构用文字描述，不要输出 Mermaid 源码。";
        }
        if (!renderer.available()) {
            return "绘图不可用（后端未找到可用的浏览器）。请改用文字描述结构，不要输出代码块。";
        }
        var rendered = renderer.render(source);
        if (rendered.isEmpty()) {
            return "绘图失败（源码语法错误或渲染超时）。请修正语法后最多再试一次；"
                    + "仍失败就改用文字描述结构，不要输出代码块。";
        }
        String relative = storage.save(sessionId, rendered.get().png(), "png");
        String label = alt == null || alt.isBlank() ? "示意图" : alt.strip();
        label = label.replace("[", "（").replace("]", "）");
        String markdown = "![" + label + "](/api/images/" + relative + ")";
        cache.put(source, markdown);
        links.add(markdown);
        if (onRendered != null) {
            onRendered.accept(markdown);
        }
        return "已生成图片（" + rendered.get().width() + "×" + rendered.get().height()
                + "），把下面的 Markdown 原样放进答案对应段落：" + markdown;
    }
}
