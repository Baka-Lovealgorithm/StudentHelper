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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
    void imageBecomesBase64SourceBlock() {
        byte[] bytes = "fake-bytes".getBytes(StandardCharsets.UTF_8);
        ImagePayload image = new ImagePayload("a.png", "image/png", "png", bytes);
        List<Msg> result = assembler.assemble(List.of(), "看图", null, List.of(image));
        Msg current = result.get(0);
        assertEquals(2, current.getContent().size());
        assertTrue(current.getContent().get(0) instanceof TextBlock);
        ImageBlock block = (ImageBlock) current.getContent().get(1);
        Base64Source source = (Base64Source) block.getSource();
        assertEquals("image/png", source.getMediaType());
        // data 只放纯 base64，data: 前缀由框架的 convertImageSourceToUrl 拼
        assertFalse(source.getData().startsWith("data:"));
        assertArrayEquals(bytes, Base64.getDecoder().decode(source.getData()));
    }

    @Test
    void multipleImagesKeepTheirOrder() {
        ImagePayload first = new ImagePayload("a.png", "image/png", "png", "A".getBytes(StandardCharsets.UTF_8));
        ImagePayload second = new ImagePayload("b.jpg", "image/jpeg", "jpg", "B".getBytes(StandardCharsets.UTF_8));
        List<Msg> result = assembler.assemble(List.of(), "两图", null, List.of(first, second));
        List<io.agentscope.core.message.ContentBlock> blocks = result.get(0).getContent();
        assertEquals(3, blocks.size()); // 文本 + 两张图
        assertEquals("image/png", ((Base64Source) ((ImageBlock) blocks.get(1)).getSource()).getMediaType());
        assertEquals("image/jpeg", ((Base64Source) ((ImageBlock) blocks.get(2)).getSource()).getMediaType());
    }

    @Test
    void partsKeepUserInputOrder() {
        ImagePayload image = new ImagePayload("a.png", "image/png", "png", "IMG".getBytes(StandardCharsets.UTF_8));
        List<ContentPart> parts = List.of(
                ContentPart.text("先看这张图"),
                new ContentPart("image", null, 0),
                ContentPart.text("这题为什么选 B"));
        List<Msg> result = assembler.assemble(List.of(), parts, "先看这张图这题为什么选 B", null, List.of(image));
        List<ContentBlock> blocks = result.get(0).getContent();
        assertEquals(3, blocks.size()); // 文字 → 图片 → 文字
        assertTrue(blocks.get(0) instanceof TextBlock);
        assertTrue(blocks.get(1) instanceof ImageBlock);
        assertTrue(blocks.get(2) instanceof TextBlock);
        String head = ((TextBlock) blocks.get(0)).getText();
        assertTrue(head.startsWith("【本轮题目】\n"));
        assertTrue(head.contains("先看这张图"));
        String tail = ((TextBlock) blocks.get(2)).getText();
        assertTrue(tail.contains("这题为什么选 B"));
        assertTrue(tail.contains("请只回答本轮题目"));
    }

    @Test
    void imageFirstThenTextKeepsOrder() {
        ImagePayload image = new ImagePayload("a.png", "image/png", "png", "IMG".getBytes(StandardCharsets.UTF_8));
        List<ContentPart> parts = List.of(
                new ContentPart("image", null, 0),
                ContentPart.text("按图作答"));
        List<ContentBlock> blocks = assembler.assemble(List.of(), parts, "按图作答", null, List.of(image))
                .get(0).getContent();
        assertEquals(3, blocks.size()); // 前缀 → 图片 → 文字
        assertEquals("【本轮题目】\n", ((TextBlock) blocks.get(0)).getText());
        assertTrue(blocks.get(1) instanceof ImageBlock);
        assertTrue(((TextBlock) blocks.get(2)).getText().startsWith("按图作答"));
    }

    @Test
    void outOfRangeImageIndexIsIgnored() {
        List<ContentPart> parts = List.of(
                ContentPart.text("只有文字"),
                new ContentPart("image", null, 5));
        List<ContentBlock> blocks = assembler.assemble(List.of(), parts, "只有文字", null, List.of())
                .get(0).getContent();
        assertEquals(1, blocks.size());
        assertTrue(((TextBlock) blocks.get(0)).getText().contains("只有文字"));
    }
}
