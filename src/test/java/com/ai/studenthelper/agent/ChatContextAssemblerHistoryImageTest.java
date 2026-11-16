package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.common.ImageContextPolicy;
import com.ai.studenthelper.config.ImageContextProperties;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.storage.ImageOptimizer;
import com.ai.studenthelper.infra.storage.ImageStorage;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** CR-037 / AC-504-1/2/5：历史图片窗口、占位与降级。 */
class ChatContextAssemblerHistoryImageTest {

    @TempDir Path tempDir;

    private final ImageContextProperties properties = new ImageContextProperties();
    private final ImageOptimizer optimizer = new ImageOptimizer(properties);
    private final ImageContextPolicy policy = new ImageContextPolicy(properties);
    private final ImageStorage storage = mock(ImageStorage.class);
    private final S2FeatureService features = mock(S2FeatureService.class);
    private ChatContextAssembler assembler;
    private Path imageFile;

    @BeforeEach
    void setUp() throws Exception {
        imageFile = tempDir.resolve("a.png");
        ImageIO.write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "png", imageFile.toFile());
        when(storage.resolve(eq(9L), anyString())).thenReturn(Optional.of(imageFile));
        when(features.isEnabled(S2Feature.HISTORY_IMAGE)).thenReturn(true);
        assembler = new ChatContextAssembler(storage, optimizer, features, policy);
    }

    private static Message user(long id, String content, String paths) {
        Message message = new Message();
        message.setId(id);
        message.setRole("USER");
        message.setStatus("SUCCESS");
        message.setContent(content);
        message.setImagePaths(paths);
        return message;
    }

    private static long imageCount(Msg msg) {
        return msg.getContent() == null ? 0
                : msg.getContent().stream().filter(block -> block instanceof ImageBlock).count();
    }

    private static List<Msg> historyOf(ChatContextAssembler assembler, List<Message> history) {
        List<Msg> all = assembler.assemble(history, null, true, List.of(), "本轮", null, List.of());
        return all.subList(0, all.size() - 1);   // 去掉当前轮 USER 消息
    }

    @Test
    void twoMostRecentImageMessagesCarryImageBlocks() {
        List<Message> history = List.of(
                user(1, "第一题", "9/a.png"),
                user(2, "第二题", "9/b.png"),
                user(3, "第三题", "9/c.png"));
        List<Msg> messages = historyOf(assembler, history);
        assertEquals(3, messages.size());
        assertEquals(0, imageCount(messages.get(0)), "最老一轮应只有占位");
        assertTrue(messages.get(0).getTextContent().contains("图片内容未随上下文提供"));
        assertEquals(1, imageCount(messages.get(1)));
        assertEquals(1, imageCount(messages.get(2)));
    }

    @Test
    void imageCapStopsAtFour() {
        List<Message> history = List.of(
                user(1, "老题", "9/a.png,9/b.png"),
                user(2, "中题", "9/c.png,9/d.png"),
                user(3, "新题", "9/e.png,9/f.png"));
        List<Msg> messages = historyOf(assembler, history);
        assertEquals(0, imageCount(messages.get(0)), "第 3 轮会超过 4 张上限");
        assertEquals(2, imageCount(messages.get(1)));
        assertEquals(2, imageCount(messages.get(2)));
    }

    @Test
    void pureImageQuestionNeverProducesEmptyMessage() {
        List<Message> history = List.of(
                user(1, null, "9/old.png"),
                user(2, "第二题", "9/b.png"),
                user(3, "第三题", "9/c.png"));
        List<Msg> messages = historyOf(assembler, history);
        assertEquals(3, messages.size(), "纯图片题不再被整条跳过");
        assertTrue(messages.get(0).getTextContent().contains("图片内容未随上下文提供"));
    }

    @Test
    void keptPureImageQuestionGetsPromptTextAndImage() {
        List<Message> history = List.of(user(1, null, "9/a.png"));
        List<Msg> messages = historyOf(assembler, history);
        assertEquals(1, imageCount(messages.get(0)));
        assertTrue(messages.get(0).getTextContent().contains("本轮题目见所附图片"));
    }

    @Test
    void missingFileFallsBackToPlaceholderWithoutThrowing() {
        when(storage.resolve(eq(9L), anyString())).thenReturn(Optional.empty());
        List<Message> history = List.of(user(1, "带图题", "9/missing.png"));
        List<Msg> messages = historyOf(assembler, history);
        assertEquals(0, imageCount(messages.get(0)));
        assertTrue(messages.get(0).getTextContent().contains("图片内容未随上下文提供"));
    }

    @Test
    void closedSwitchReturnsVerbatimLegacyBehaviour() {
        when(features.isEnabled(S2Feature.HISTORY_IMAGE)).thenReturn(false);
        List<Message> history = List.of(
                user(1, "第一题", "9/a.png"),
                user(2, null, "9/b.png"),
                user(3, "第三题", "9/c.png"));
        List<Msg> messages = historyOf(assembler, history);
        assertEquals(2, messages.size(), "content 为 null 的消息应整条跳过");
        for (Msg msg : messages) {
            assertEquals(0, imageCount(msg));
            assertFalse(msg.getTextContent().contains("图片内容未随上下文提供"));
        }
    }

    @Test
    void currentRoundImageIsOptimizedBeforeEncoding() throws Exception {
        BufferedImage big = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(7);
        for (int y = 0; y < big.getHeight(); y += 2) {
            for (int x = 0; x < big.getWidth(); x += 2) {
                int rgb = random.nextInt(0xFFFFFF);
                big.setRGB(x, y, rgb);
                big.setRGB(Math.min(x + 1, big.getWidth() - 1), y, rgb);
                big.setRGB(x, Math.min(y + 1, big.getHeight() - 1), rgb);
                big.setRGB(Math.min(x + 1, big.getWidth() - 1), Math.min(y + 1, big.getHeight() - 1), rgb);
            }
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ImageIO.write(big, "png", out);
        byte[] source = out.toByteArray();
        var image = new com.ai.studenthelper.common.dto.ImagePayload(
                "big.png", "image/png", "png", source);
        List<Msg> all = assembler.assemble(List.of(), null, true, List.of(),
                "本轮", null, List.of(image));
        ImageBlock block = (ImageBlock) all.get(0).getContent().get(1);
        Base64Source src = (Base64Source) block.getSource();
        assertEquals("image/jpeg", src.getMediaType(), "超阈值当前轮图片应被压缩为 JPEG");
        byte[] encoded = Base64.getDecoder().decode(src.getData());
        assertTrue(encoded.length < source.length);
    }
}
