package com.ai.studenthelper.agent;

import com.ai.studenthelper.common.dto.ImagePayload;
import com.ai.studenthelper.domain.Message;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** 会话历史 → 模型消息；失败消息不进上下文，图片转 data URL（技术规范 4.4、7.2）。 */
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
            messages.add(Msg.builder().role(role).textContent(message.getContent()).build());
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
            String dataUrl = "data:" + image.mimeType() + ";base64,"
                    + Base64.getEncoder().encodeToString(image.bytes());
            blocks.add(new ImageBlock(new URLSource(dataUrl, image.mimeType())));
        }
        messages.add(Msg.builder().role(MsgRole.USER).content(blocks).build());
        return messages;
    }
}
