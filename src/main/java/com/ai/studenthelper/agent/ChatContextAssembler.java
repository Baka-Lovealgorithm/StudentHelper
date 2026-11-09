package com.ai.studenthelper.agent;

import com.ai.studenthelper.common.dto.ContentPart;
import com.ai.studenthelper.common.dto.ImagePayload;
import com.ai.studenthelper.domain.Message;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** 会话历史 → 模型消息；失败消息不进上下文，文字与图片按输入顺序交替（技术规范 4.4、4.6）。 */
@Component
public class ChatContextAssembler {

    /** 最多携带最近 12 条历史消息（6 轮），避免多轮追问把上下文撑爆。 */
    private static final int MAX_HISTORY_MESSAGES = 12;

    /** 兼容旧调用：整段文字作为唯一文本段。 */
    public List<Msg> assemble(List<Message> history, String text, String hint, List<ImagePayload> images) {
        return assemble(history, List.of(), text, hint, images);
    }

    /**
     * 按 {@code parts} 的顺序装配本轮用户消息：文字段与图片段交替，顺序即用户输入顺序。
     * {@code parts} 为空时退化为「整段文字 + 图片列表」的旧行为。
     */
    public List<Msg> assemble(List<Message> history,
                              List<ContentPart> parts,
                              String text,
                              String hint,
                              List<ImagePayload> images) {
        List<Msg> messages = new ArrayList<>();
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < history.size(); i++) {
            Message message = history.get(i);
            if (!"SUCCESS".equals(message.getStatus()) || message.getContent() == null) {
                continue;
            }
            MsgRole role = "ASSISTANT".equals(message.getRole()) ? MsgRole.ASSISTANT : MsgRole.USER;
            // 历史里的「本内容由 AI 生成」是后端追加的，带进上下文会让模型模仿并在新一轮自己输出一次
            String content = role == MsgRole.ASSISTANT
                    ? SolutionSectionParser.stripDisclaimer(message.getContent())
                    : message.getContent();
            messages.add(Msg.builder().role(role).textContent(content).build());
        }

        messages.add(Msg.builder().role(MsgRole.USER)
                .content(buildBlocks(parts, text, hint, images)).build());
        return messages;
    }

    /** 前缀固定在最前，之后按 parts 顺序交替，hint 与收口指令追加在末尾。 */
    private List<ContentBlock> buildBlocks(List<ContentPart> parts,
                                           String text,
                                           String hint,
                                           List<ImagePayload> images) {
        // 无有序段（旧客户端 / 重新生成）时沿用 S1 的「文字块（含 hint 与收口指令）+ 全部图片」形态
        if (parts == null || parts.isEmpty()) {
            return legacyBlocks(text, hint, images);
        }

        List<ContentBlock> blocks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder("【本轮题目】\n");
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
                blocks.add(toImageBlock(images.get(index)));
            } else if (part.value() != null) {
                buffer.append(part.value());
            }
        }

        if (hint != null && !hint.isBlank()) {
            buffer.append("\n\n【本轮难度与讲解要求】\n").append(hint.trim());
        }
        buffer.append("\n\n请只回答本轮题目，严格遵守系统给定的段落结构。");
        if (buffer.length() > 0) {
            blocks.add(TextBlock.builder().text(buffer.toString()).build());
        }
        return blocks;
    }

    /** S1 行为：全部文字（含 hint 与收口指令）在前，图片按列表顺序在后，与既有实现逐字一致。 */
    private List<ContentBlock> legacyBlocks(String text, String hint, List<ImagePayload> images) {
        List<ContentBlock> blocks = new ArrayList<>();
        StringBuilder userText = new StringBuilder();
        userText.append("【本轮题目】\n").append(text == null ? "" : text);
        if (hint != null && !hint.isBlank()) {
            userText.append("\n\n【本轮难度与讲解要求】\n").append(hint.trim());
        }
        userText.append("\n\n请只回答本轮题目，严格遵守系统给定的段落结构。");
        blocks.add(TextBlock.builder().text(userText.toString()).build());
        for (ImagePayload image : images) {
            blocks.add(toImageBlock(image));
        }
        return blocks;
    }

    /** Base64Source 由框架拼 data URI，避免把 base64 冒充成 URL（技术规范 TCR-028）。 */
    private ImageBlock toImageBlock(ImagePayload image) {
        return new ImageBlock(Base64Source.builder()
                .mediaType(image.mimeType())
                .data(Base64.getEncoder().encodeToString(image.bytes()))
                .build());
    }
}
