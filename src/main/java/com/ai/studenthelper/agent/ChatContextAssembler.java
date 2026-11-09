package com.ai.studenthelper.agent;

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

/** 会话历史 → 模型消息；失败消息不进上下文，图片以 Base64Source 内联（技术规范 4.4、7.2）。 */
@Component
public class ChatContextAssembler {

    /** 最多携带最近 12 条历史消息（6 轮），避免多轮追问把上下文撑爆。 */
    private static final int MAX_HISTORY_MESSAGES = 12;

    public List<Msg> assemble(List<Message> history, String text, String hint, List<ImagePayload> images) {
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

        List<ContentBlock> blocks = new ArrayList<>();
        StringBuilder userText = new StringBuilder();
        userText.append("【本轮题目】\n").append(text == null ? "" : text);
        if (hint != null && !hint.isBlank()) {
            userText.append("\n\n【本轮难度与讲解要求】\n").append(hint.trim());
        }
        userText.append("\n\n请只回答本轮题目，严格遵守系统给定的段落结构。");
        blocks.add(TextBlock.builder().text(userText.toString()).build());

        for (ImagePayload image : images) {
            // Base64Source 由框架拼 data URI，避免把 base64 冒充成 URL（技术规范 TCR-028）
            blocks.add(new ImageBlock(Base64Source.builder()
                    .mediaType(image.mimeType())
                    .data(Base64.getEncoder().encodeToString(image.bytes()))
                    .build()));
        }
        messages.add(Msg.builder().role(MsgRole.USER).content(blocks).build());
        return messages;
    }
}
