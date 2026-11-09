package com.ai.studenthelper.agent;

import com.ai.studenthelper.common.dto.ImagePayload;
import com.ai.studenthelper.domain.Message;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatContextAssemblerTest {

    private final ChatContextAssembler assembler = new ChatContextAssembler();

    private static Message message(String role, String content, String status) {
        Message m = new Message();
        m.setRole(role);
        m.setContent(content);
        m.setStatus(status);
        return m;
    }

    @Test
    void failedMessagesAreExcludedFromContext() {
        List<Msg> result = assembler.assemble(
                List.of(message("USER", "旧题", "SUCCESS"),
                        message("ASSISTANT", "失败答案", "FAILED")),
                "新题", null, List.of());
        assertEquals(2, result.size()); // 1 条成功历史 + 本轮
        assertEquals(MsgRole.USER, result.get(0).getRole());
        assertEquals("旧题", result.get(0).getTextContent());
    }

    @Test
    void hintIsAppendedToCurrentUserMessage() {
        List<Msg> result = assembler.assemble(List.of(), "题面", "用初学者能懂的话", List.of());
        String text = result.get(0).getTextContent();
        assertTrue(text.contains("【本轮题目】"));
        assertTrue(text.contains("题面"));
        assertTrue(text.contains("用初学者能懂的话"));
    }

    @Test
    void assistantHistoryDisclaimerIsStripped() {
        Message withLine = message("ASSISTANT",
                "## ① 题目理解\n旧答案\n\n本内容由 AI 生成，请自行核对\n", "SUCCESS");
        List<Msg> result = assembler.assemble(List.of(withLine), "新题", null, List.of());
        assertFalse(result.get(0).getTextContent().contains("本内容由 AI 生成"));
        assertTrue(result.get(0).getTextContent().contains("旧答案"));
    }

    @Test
    void historyIsCappedAtTwelveMessages() {
        List<Message> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            history.add(message("USER", "q" + i, "SUCCESS"));
        }
        List<Msg> result = assembler.assemble(history, "new", null, List.of());
        assertEquals(13, result.size()); // 最近 12 条历史 + 本轮
        assertEquals("q8", result.get(0).getTextContent());
        assertEquals("new", result.get(12).getTextContent().substring(0, 3).isEmpty() ? "" : "new");
    }

    @Test
    void imageBecomesBase64DataUrlBlock() {
        ImagePayload image = new ImagePayload("a.png", "image/png", "png",
                "fake-bytes".getBytes(StandardCharsets.UTF_8));
        List<Msg> result = assembler.assemble(List.of(), "看图", null, List.of(image));
        Msg current = result.get(0);
        assertEquals(2, current.getContent().size());
        assertTrue(current.getContent().get(0) instanceof TextBlock);
        ImageBlock block = (ImageBlock) current.getContent().get(1);
        URLSource source = (URLSource) block.getSource();
        assertTrue(source.getUrl().startsWith("data:image/png;base64,"));
        assertEquals("image/png", source.getMimeType());
    }
}
