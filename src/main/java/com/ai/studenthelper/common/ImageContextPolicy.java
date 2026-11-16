package com.ai.studenthelper.common;

import com.ai.studenthelper.config.ImageContextProperties;
import com.ai.studenthelper.domain.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CR-037：历史图片窗口策略的唯一实现，装配器与对话压缩共用，保证口径一致。
 *
 * <p>按 USER 消息倒序取最近 N 条带图消息，从最近开始累计图片数，再加一条会超过
 * {@code maxHistoryImages} 时停止；未入选的消息渲染为文字占位。</p>
 */
@Component
public class ImageContextPolicy {

    private static final String PLACEHOLDER =
            "（本轮含 %d 张图片，图片内容未随上下文提供，请结合当时文字与解答作答）";

    /** keepIds：保留原图的消息 id；imageCount：随之进入上下文的图片总数。 */
    public record Selection(Set<Long> keepIds, int imageCount) {
    }

    private final ImageContextProperties properties;

    public ImageContextPolicy(ImageContextProperties properties) {
        this.properties = properties;
    }

    public Selection select(List<Message> history) {
        return select(history, properties.getRecentImageMessages(), properties.getMaxHistoryImages());
    }

    public static Selection select(List<Message> history, int recentMessages, int maxImages) {
        Set<Long> keepIds = new HashSet<>();
        if (history == null || history.isEmpty() || recentMessages <= 0 || maxImages <= 0) {
            return new Selection(keepIds, 0);
        }
        int rounds = 0;
        int total = 0;
        for (int i = history.size() - 1; i >= 0 && rounds < recentMessages; i--) {
            Message message = history.get(i);
            if (message == null || !"USER".equals(message.getRole())) {
                continue;
            }
            int count = pathsOf(message).size();
            if (count == 0) {
                continue;
            }
            if (total + count > maxImages) {
                break;
            }
            if (message.getId() != null) {
                keepIds.add(message.getId());
            }
            total += count;
            rounds++;
        }
        return new Selection(keepIds, total);
    }

    /** image_paths 为逗号分隔相对路径；空值 / 空白段被忽略。 */
    public static List<String> pathsOf(Message message) {
        String raw = message == null ? null : message.getImagePaths();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }
        return paths;
    }

    public static String placeholder(int count) {
        return String.format(PLACEHOLDER, Math.max(1, count));
    }

    public int estimatedImageTokens(Selection selection) {
        return selection.imageCount() * Math.max(0, properties.getTokenPerImage());
    }
}
