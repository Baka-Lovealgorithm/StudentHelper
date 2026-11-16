package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.MemoryTools;
import com.ai.studenthelper.agent.SolverAgentFactory;
import com.ai.studenthelper.common.ImageContextPolicy;
import com.ai.studenthelper.config.ImageContextProperties;
import com.ai.studenthelper.config.MemoryProperties;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.memory.SessionSummaryStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** CR-037 / AC-504-6：图片 token 额度只在开关开启时计入压缩判定。 */
class MemoryCompactionImageBudgetTest {

    private static Message textMessage(long id, String text, String paths) {
        Message message = new Message();
        message.setId(id);
        message.setRole("USER");
        message.setStatus("SUCCESS");
        message.setContent(text);
        message.setImagePaths(paths);
        return message;
    }

    private MemoryCompactionService service(S2FeatureService features, ImageContextPolicy policy) {
        return new MemoryCompactionService(new MemoryProperties(), features,
                mock(SessionSummaryStore.class), mock(MemoryTools.class),
                mock(SolverAgentFactory.class), policy);
    }

    @Test
    void imageBudgetIsAddedWhenSwitchEnabled() {
        S2FeatureService features = mock(S2FeatureService.class);
        when(features.isEnabled(S2Feature.HISTORY_IMAGE)).thenReturn(true);
        ImageContextProperties properties = new ImageContextProperties();
        properties.setTokenPerImage(765);
        MemoryCompactionService service = service(features, new ImageContextPolicy(properties));
        List<Message> history = List.of(textMessage(1, "文字题", null),
                textMessage(2, "图片题", "9/a.png,9/b.png"));

        int withImages = service.estimateTokens(history, null);
        int textOnly = com.ai.studenthelper.common.TokenEstimator.estimateAll(
                List.of("文字题", "图片题"));
        assertEquals(1530, withImages - textOnly, "2 张图片 × 765");
    }

    @Test
    void imageBudgetIsOmittedWhenSwitchDisabled() {
        S2FeatureService features = mock(S2FeatureService.class);
        when(features.isEnabled(S2Feature.HISTORY_IMAGE)).thenReturn(false);
        ImageContextProperties properties = new ImageContextProperties();
        MemoryCompactionService service = service(features, new ImageContextPolicy(properties));
        List<Message> history = List.of(textMessage(1, "图片题", "9/a.png"));

        assertEquals(com.ai.studenthelper.common.TokenEstimator.estimate("图片题"),
                service.estimateTokens(history, null));
    }
}
