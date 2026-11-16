package com.ai.studenthelper.agent;

import com.ai.studenthelper.common.dto.ContentPart;
import com.ai.studenthelper.common.dto.ImagePayload;
import com.ai.studenthelper.domain.Message;
import io.agentscope.core.message.Msg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CR-018 / AC-501-9：追问消息不加题目前缀与收口指令；完整链路逐字保留。 */
class ChatContextAssemblerFollowUpTest {

    private final ChatContextAssembler assembler = new ChatContextAssembler();

    private static String lastUserText(List<Msg> messages) {
        return messages.get(messages.size() - 1).getTextContent();
    }

    @Test
    void legacyBlocksKeepQuestionPrefixAndClosure() {
        List<Msg> messages = assembler.assemble(List.of(), null, false, List.of(),
                "题目：栈的性质", "用初学者能懂的话", List.of());
        String text = lastUserText(messages);
        assertTrue(text.startsWith("【本轮题目】"));
        assertTrue(text.contains("【本轮难度与讲解要求】"));
        assertTrue(text.contains("请只回答本轮题目，严格遵守系统给定的段落结构。"));
    }

    @Test
    void followUpHasNoPrefixAndNoClosure() {
        List<Msg> messages = assembler.assembleFollowUp(List.of(), null, false, List.of(),
                "为什么第二步要用这个性质？", null, List.of());
        String text = lastUserText(messages);
        assertEquals("为什么第二步要用这个性质？", text);
        assertFalse(text.contains("【本轮题目】"));
        assertFalse(text.contains("严格遵守"));
    }

    @Test
    void followUpStillCarriesHintAndOrderedParts() {
        List<ContentPart> parts = List.of(
                new ContentPart("text", "先解释一下", null),
                new ContentPart("text", "，再给个例子", null));
        List<Msg> messages = assembler.assembleFollowUp(List.of(), null, false, parts,
                "备用文本", "讲通俗些", List.of());
        String text = lastUserText(messages);
        assertTrue(text.contains("先解释一下，再给个例子"));
        assertTrue(text.contains("【本轮难度与讲解要求】"));
        assertFalse(text.contains("【本轮题目】"));
    }

    @Test
    void failedHistoryMessagesAreStillSkipped() {
        Message failed = new Message();
        failed.setRole("ASSISTANT");
        failed.setStatus("FAILED");
        failed.setContent("不该进上下文");
        List<Msg> messages = assembler.assembleFollowUp(List.of(failed), null, false, List.of(),
                "为什么？", null, List.of());
        assertEquals(1, messages.size());
        assertFalse(lastUserText(messages).contains("不该进上下文"));
    }
}
