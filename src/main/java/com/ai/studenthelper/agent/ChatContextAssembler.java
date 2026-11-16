package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.common.ImageContextPolicy;
import com.ai.studenthelper.common.dto.ContentPart;
import com.ai.studenthelper.common.dto.ImagePayload;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.storage.ImageOptimizer;
import com.ai.studenthelper.infra.storage.ImageStorage;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** 会话历史 → 模型消息；失败消息不进上下文，文字与图片按输入顺序交替（技术规范 4.4、4.6、5.31）。 */
@Component
public class ChatContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ChatContextAssembler.class);

    /** 压缩开关关闭时的历史窗口上限，与 S1 行为逐字一致（AC-204-1）。 */
    private static final int MAX_HISTORY_MESSAGES = 12;

    private final ImageStorage imageStorage;
    private final ImageOptimizer optimizer;
    private final S2FeatureService features;
    private final ImageContextPolicy policy;

    /** Spring 注入；开关与压缩器均为可空，保证单测直连构造时逐字回到旧口径。 */
    @Autowired
    public ChatContextAssembler(ImageStorage imageStorage, ImageOptimizer optimizer,
                                S2FeatureService features, ImageContextPolicy policy) {
        this.imageStorage = imageStorage;
        this.optimizer = optimizer;
        this.features = features;
        this.policy = policy;
    }

    /** 兼容单测直连构造：等价于 historyImage 关闭态。 */
    public ChatContextAssembler() {
        this(null, null, null, null);
    }

    /** 兼容旧调用：整段文字作为唯一文本段。 */
    public List<Msg> assemble(List<Message> history, String text, String hint, List<ImagePayload> images) {
        return assemble(history, List.of(), text, hint, images);
    }

    /** 兼容旧调用：无摘要、按 S1 的 12 条窗口装配（fullWindow=false）。 */
    public List<Msg> assemble(List<Message> history,
                              List<ContentPart> parts,
                              String text,
                              String hint,
                              List<ImagePayload> images) {
        return assemble(history, null, false, parts, text, hint, images);
    }

    /**
     * T-401 / TCR-040：带压缩摘要的装配。
     *
     * <p>{@code fullWindow=true} 表示历史已由 {@code MemoryCompactionService} 按阈值收敛过，
     * 可以整段带入，不再套 12 条硬截断——否则会出现「既不压缩也不带入」的丢弃空档
     * （原 13~30 条区间）。{@code fullWindow=false} 表示压缩开关关闭，回到 S1 的 12 条口径。</p>
     */
    public List<Msg> assemble(List<Message> history,
                              String summary,
                              boolean fullWindow,
                              List<ContentPart> parts,
                              String text,
                              String hint,
                              List<ImagePayload> images) {
        List<Msg> messages = appendHistory(new ArrayList<>(), history, summary, fullWindow);
        messages.add(Msg.builder().role(MsgRole.USER)
                .content(buildBlocks(parts, text, hint, images)).build());
        return messages;
    }

    /**
     * CR-039 知识点讲解：历史装配与解题一致，本轮消息用「【本轮问题】」前缀与讲解结构收口，
     * 不出现「题目 / 解题」措辞，避免模型把它当成一道题去解。
     */
    public List<Msg> assembleExplain(List<Message> history,
                                     String summary,
                                     boolean fullWindow,
                                     List<ContentPart> parts,
                                     String text,
                                     String hint,
                                     List<ImagePayload> images) {
        List<Msg> messages = appendHistory(new ArrayList<>(), history, summary, fullWindow);
        messages.add(Msg.builder().role(MsgRole.USER)
                .content(buildBlocks(parts, text, hint, images,
                        "【本轮问题】\n", "请只回答本轮问题，严格遵守上面给定的讲解结构。")).build());
        return messages;
    }

    /**
     * CR-018 追问：历史装配与完整链路一致，但本轮用户消息**不加**「【本轮题目】」前缀，
     * 也不加「请只回答本轮题目，严格遵守系统给定的段落结构」收口指令。
     */
    public List<Msg> assembleFollowUp(List<Message> history,
                                      String summary,
                                      boolean fullWindow,
                                      List<ContentPart> parts,
                                      String text,
                                      String hint,
                                      List<ImagePayload> images) {
        List<Msg> messages = appendHistory(new ArrayList<>(), history, summary, fullWindow);
        messages.add(Msg.builder().role(MsgRole.USER)
                .content(buildFollowUpBlocks(parts, text, hint, images)).build());
        return messages;
    }

    private boolean historyImagesEnabled() {
        return features != null && features.isEnabled(S2Feature.HISTORY_IMAGE)
                && policy != null && imageStorage != null && optimizer != null;
    }

    private List<Msg> appendHistory(List<Msg> messages, List<Message> history,
                                    String summary, boolean fullWindow) {
        if (summary != null && !summary.isBlank()) {
            messages.add(Msg.builder().role(MsgRole.SYSTEM)
                    .textContent("【历史对话摘要】\n" + summary.trim()).build());
        }
        int start = fullWindow ? 0 : Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        boolean enabled = historyImagesEnabled();
        ImageContextPolicy.Selection selection = enabled && policy != null
                ? policy.select(history.subList(start, history.size()))
                : null;
        int placeholders = 0;
        for (int i = start; i < history.size(); i++) {
            Message message = history.get(i);
            if (!"SUCCESS".equals(message.getStatus())) {
                continue;
            }
            MsgRole role = "ASSISTANT".equals(message.getRole()) ? MsgRole.ASSISTANT : MsgRole.USER;
            String raw = message.getContent();
            if (!enabled) {
                // CR-036 后的原口径：纯文本、content 为 null 整条跳过
                if (raw == null) {
                    continue;
                }
                String content = role == MsgRole.ASSISTANT
                        ? SolutionSectionParser.stripDisclaimer(raw) : raw;
                messages.add(Msg.builder().role(role).textContent(content).build());
                continue;
            }
            String content = raw == null ? "" : (role == MsgRole.ASSISTANT
                    ? SolutionSectionParser.stripDisclaimer(raw) : raw);
            List<String> paths = ImageContextPolicy.pathsOf(message);
            boolean keep = message.getId() != null && selection.keepIds().contains(message.getId());
            List<ContentBlock> imageBlocks = keep ? historyImageBlocks(message, paths) : List.of();

            String body = content;
            if (!paths.isEmpty() && !keep) {
                // 更早轮次：文字占位；纯图片题也不再产生空消息
                String placeholder = ImageContextPolicy.placeholder(paths.size());
                body = content.isBlank() ? placeholder : content.trim() + "\n" + placeholder;
                placeholders++;
            } else if (keep && imageBlocks.isEmpty() && !paths.isEmpty()) {
                // 文件缺失 / 解码失败：静默退回占位，不阻断解题
                String placeholder = ImageContextPolicy.placeholder(paths.size());
                body = content.isBlank() ? placeholder : content.trim() + "\n" + placeholder;
                placeholders++;
            } else if (keep && content.isBlank()) {
                body = "（本轮题目见所附图片）";
            }

            List<ContentBlock> blocks = new ArrayList<>();
            if (!body.isBlank()) {
                blocks.add(TextBlock.builder().text(body).build());
            }
            blocks.addAll(imageBlocks);
            if (blocks.isEmpty()) {
                continue;
            }
            messages.add(Msg.builder().role(role).content(blocks).build());
        }
        if (enabled && selection != null) {
            log.info("HISTORY_IMAGE_ASSEMBLY keptMessages={} keptImages={} placeholders={}",
                    selection.keepIds().size(), selection.imageCount(), placeholders);
        }
        return messages;
    }

    /**
     * CR-037：按 image_paths 的相对路径读回原图，逐张压缩后转 ImageBlock。
     * 任一张失败只记 warn 并跳过；全部失败时由调用方追加文字占位。
     */
    private List<ContentBlock> historyImageBlocks(Message message, List<String> paths) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (imageStorage == null) {
            return blocks;
        }
        for (String path : paths) {
            int slash = path.lastIndexOf('/');
            if (slash <= 0 || slash == path.length() - 1) {
                continue;
            }
            Long sessionId;
            try {
                sessionId = Long.parseLong(path.substring(0, slash).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            String fileName = path.substring(slash + 1);
            String mime = ImageOptimizer.mimeOfFileName(fileName);
            if (mime == null) {
                continue;
            }
            try {
                Optional<Path> resolved = imageStorage.resolve(sessionId, fileName);
                if (resolved.isEmpty()) {
                    log.warn("HISTORY_IMAGE_MISSING messageId={} path={}", message.getId(), path);
                    continue;
                }
                byte[] bytes = Files.readAllBytes(resolved.get());
                ImageOptimizer.Optimized optimized = optimizer.optimize(bytes, mime);
                blocks.add(new ImageBlock(Base64Source.builder()
                        .mediaType(optimized.mimeType())
                        .data(Base64.getEncoder().encodeToString(optimized.bytes()))
                        .build()));
            } catch (Exception e) {
                log.warn("HISTORY_IMAGE_FAILED messageId={} path={} reason={}",
                        message.getId(), path, e.toString());
            }
        }
        return blocks;
    }

    /** 前缀固定在最前，之后按 parts 顺序交替，hint 与收口指令追加在末尾。 */
    private List<ContentBlock> buildBlocks(List<ContentPart> parts,
                                           String text,
                                           String hint,
                                           List<ImagePayload> images) {
        return buildBlocks(parts, text, hint, images,
                "【本轮题目】\n", "请只回答本轮题目，严格遵守系统给定的段落结构。");
    }

    /** CR-039：前缀与收口指令由形态决定（题目 / 问题）。 */
    private List<ContentBlock> buildBlocks(List<ContentPart> parts,
                                           String text,
                                           String hint,
                                           List<ImagePayload> images,
                                           String prefix,
                                           String closing) {
        // CR-043：当前轮图片的压缩开关只读一次，不再每张图各查一次库
        boolean optimizeImages = historyImagesEnabled();
        // 无有序段（旧客户端 / 重新生成）时沿用 S1 的「文字块（含 hint 与收口指令）+ 全部图片」形态
        if (parts == null || parts.isEmpty()) {
            return legacyBlocks(text, hint, images, prefix, closing, optimizeImages);
        }

        List<ContentBlock> blocks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder(prefix);
        appendOrdered(blocks, buffer, parts, images, optimizeImages);
        if (hint != null && !hint.isBlank()) {
            buffer.append("\n\n【本轮难度与讲解要求】\n").append(hint.trim());
        }
        buffer.append("\n\n").append(closing);
        if (buffer.length() > 0) {
            blocks.add(TextBlock.builder().text(buffer.toString()).build());
        }
        return blocks;
    }

    /** 追问：纯题面 + 可选难度要求，无题目前缀、无收口指令。 */
    private List<ContentBlock> buildFollowUpBlocks(List<ContentPart> parts,
                                                   String text,
                                                   String hint,
                                                   List<ImagePayload> images) {
        // CR-043：追问轮的图片压缩开关同样只读一次
        boolean optimizeImages = historyImagesEnabled();
        List<ContentBlock> blocks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        if (parts == null || parts.isEmpty()) {
            buffer.append(text == null ? "" : text);
        } else {
            appendOrdered(blocks, buffer, parts, images, optimizeImages);
        }
        if (hint != null && !hint.isBlank()) {
            buffer.append("\n\n【本轮难度与讲解要求】\n").append(hint.trim());
        }
        String body = buffer.toString().trim();
        if (!body.isEmpty()) {
            blocks.add(TextBlock.builder().text(body).build());
        }
        if (parts == null || parts.isEmpty()) {
            for (ImagePayload image : images) {
                blocks.add(toImageBlock(image, optimizeImages));
            }
        }
        return blocks;
    }

    /** 按 parts 顺序把文字段与图片段交替放入 blocks，剩余文字留在 buffer。 */
    private void appendOrdered(List<ContentBlock> blocks, StringBuilder buffer,
                               List<ContentPart> parts, List<ImagePayload> images,
                               boolean optimizeImages) {
        for (ContentPart part : parts) {
            if (part == null) {
                continue;
            }
            if (part.isImage()) {
                int index = part.index() == null ? -1 : part.index();
                // 下标越界说明前后端不一致，忽略该图但不阻断解题
                if (index < 0 || index >= images.size()) {
                    continue;
                }
                if (buffer.length() > 0) {
                    blocks.add(TextBlock.builder().text(buffer.toString()).build());
                    buffer.setLength(0);
                }
                blocks.add(toImageBlock(images.get(index), optimizeImages));
            } else if (part.value() != null) {
                buffer.append(part.value());
            }
        }
    }

    /** S1 行为：全部文字（含 hint 与收口指令）在前，图片按列表顺序在后，与既有实现逐字一致。 */
    private List<ContentBlock> legacyBlocks(String text, String hint, List<ImagePayload> images) {
        return legacyBlocks(text, hint, images, "【本轮题目】\n",
                "请只回答本轮题目，严格遵守系统给定的段落结构。", historyImagesEnabled());
    }

    private List<ContentBlock> legacyBlocks(String text, String hint, List<ImagePayload> images,
                                            String prefix, String closing, boolean optimizeImages) {
        List<ContentBlock> blocks = new ArrayList<>();
        StringBuilder userText = new StringBuilder();
        userText.append(prefix).append(text == null ? "" : text);
        if (hint != null && !hint.isBlank()) {
            userText.append("\n\n【本轮难度与讲解要求】\n").append(hint.trim());
        }
        userText.append("\n\n").append(closing);
        blocks.add(TextBlock.builder().text(userText.toString()).build());
        for (ImagePayload image : images) {
            blocks.add(toImageBlock(image, optimizeImages));
        }
        return blocks;
    }

    /** Base64Source 由框架拼 data URI；CR-037 起当前轮图片也统一过一次尺寸压缩。 */
    private ImageBlock toImageBlock(ImagePayload image, boolean optimizeImages) {
        ImageOptimizer.Optimized optimized = optimizeImages
                ? optimizer.optimize(image.bytes(), image.mimeType())
                : ImageOptimizer.Optimized.original(image.bytes(), image.mimeType(), image.extension());
        return new ImageBlock(Base64Source.builder()
                .mediaType(optimized.mimeType())
                .data(Base64.getEncoder().encodeToString(optimized.bytes()))
                .build());
    }
}
