package com.ai.studenthelper.common;

import com.ai.studenthelper.config.ImageContextProperties;
import com.ai.studenthelper.domain.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CR-037：历史图片窗口策略（最近 2 轮 + ≤4 张）。 */
class ImageContextPolicyTest {

    private static Message user(long id, String content, String paths) {
        Message message = new Message();
        message.setId(id);
        message.setRole("USER");
        message.setStatus("SUCCESS");
        message.setContent(content);
        message.setImagePaths(paths);
        return message;
    }

    @Test
    void keepsTwoMostRecentImageMessages() {
        List<Message> history = List.of(
                user(1, "第一题", "1/a.png"),
                user(2, "第二题", "1/b.png"),
                user(3, "第三题", "1/c.png"));
        ImageContextPolicy.Selection selection = ImageContextPolicy.select(history, 2, 4);
        assertEquals(2, selection.keepIds().size());
        assertTrue(selection.keepIds().contains(2L));
        assertTrue(selection.keepIds().contains(3L));
        assertFalse(selection.keepIds().contains(1L));
        assertEquals(2, selection.imageCount());
    }

    @Test
    void stopsWhenTotalImagesWouldExceedCap() {
        List<Message> history = List.of(
                user(1, "老题", "1/a.png"),
                user(2, "中题", "1/a.png,1/b.png,1/c.png"),
                user(3, "新题", "1/a.png,1/b.png"));
        ImageContextPolicy.Selection selection = ImageContextPolicy.select(history, 2, 4);
        assertTrue(selection.keepIds().contains(3L));
        assertFalse(selection.keepIds().contains(2L), "再加 3 张会超过 4 张上限");
        assertEquals(2, selection.imageCount());
    }

    @Test
    void assistantAndEmptyMessagesAreIgnored() {
        Message assistant = user(1, "解答", null);
        assistant.setRole("ASSISTANT");
        List<Message> history = List.of(assistant, user(2, "文字题", null), user(3, "图片题", "3/a.png"));
        ImageContextPolicy.Selection selection = ImageContextPolicy.select(history, 2, 4);
        assertEquals(1, selection.keepIds().size());
        assertTrue(selection.keepIds().contains(3L));
        assertEquals(1, selection.imageCount());
    }

    @Test
    void pathsOfSplitsAndTrims() {
        Message message = user(1, "", " 9/a.png , 9/b.jpg , ");
        assertEquals(List.of("9/a.png", "9/b.jpg"), ImageContextPolicy.pathsOf(message));
        assertTrue(ImageContextPolicy.placeholder(3).contains("3 张图片"));
    }

    @Test
    void estimatedTokensUsesConfiguredPerImageBudget() {
        ImageContextProperties properties = new ImageContextProperties();
        properties.setTokenPerImage(765);
        ImageContextPolicy policy = new ImageContextPolicy(properties);
        assertEquals(1530, policy.estimatedImageTokens(
                ImageContextPolicy.select(List.of(user(1, "题", "1/a.png,1/b.png")), 2, 4)));
    }
}
