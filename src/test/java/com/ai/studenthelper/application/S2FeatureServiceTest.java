package com.ai.studenthelper.application;

import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.enums.S2Feature;
import com.ai.studenthelper.infra.persistence.FeatureToggleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DD-01：逐功能独立开关、默认关闭、关闭态拒绝并给出 E3004。 */
@ExtendWith(MockitoExtension.class)
class S2FeatureServiceTest {

    @Mock
    private FeatureToggleRepository repository;

    @Test
    void listReturnsAllFeaturesAndDefaultsToClosedWhenRowMissing() {
        when(repository.findAll()).thenReturn(Map.of("usagePanel", true));
        var rows = new S2FeatureService(repository).list();

        assertEquals(12, rows.size(),
                "CR-019 移除 subjectTemplate（16 → 15）、CR-024 退役 optionalSections（15 → 14）、"
                        + "CR-037 新增 historyImage（14 → 15）、CR-039 移除 subjectSkill（15 → 14）、"
                        + "CR-041 退役 feedback（14 → 13）、CR-042 退役 workspace（13 → 12）");
        assertTrue(rows.stream().anyMatch(r -> r.key().equals("historyImage")),
                "CR-037：历史图片入上下文开关已登记");
        assertFalse(rows.stream().anyMatch(r -> r.key().equals("optionalSections")),
                "CR-024：可选输出段落开关已退役");
        assertTrue(rows.stream().anyMatch(r -> r.key().equals("mcp")));
        assertTrue(rows.stream().anyMatch(r -> r.key().equals("learningStats")));
        assertTrue(rows.stream().anyMatch(r -> r.key().equals("conversationCompaction")));
        assertTrue(rows.stream().noneMatch(r -> r.key().equals("subjectSkill")),
                "CR-039：学科规范开关已移除，学科规范恒可用");
        assertTrue(rows.stream().noneMatch(r -> r.key().equals("feedback")),
                "CR-041：反馈闭环已退役，开关不再出现（错题本 wrongBook 保留）");
        assertTrue(rows.stream().noneMatch(r -> r.key().equals("workspace")),
                "CR-042：文件工作区已退役（功能从未实现），开关不再出现");
        assertFalse(rows.stream().anyMatch(r -> r.key().equals("subjectTemplate")),
                "CR-019：旧学科开关不再出现在功能开关列表");
        assertTrue(rows.stream().anyMatch(r -> r.key().equals("usagePanel") && r.enabled()));
        assertTrue(rows.stream().filter(r -> !r.key().equals("usagePanel"))
                .allMatch(r -> !r.enabled()), "数据库缺行时按关闭处理");
    }

    @Test
    void requireEnabledThrowsE3004WhenClosed() {
        when(repository.isEnabled("knowledgeExtract")).thenReturn(false);
        var service = new S2FeatureService(repository);

        assertFalse(service.isEnabled(S2Feature.KNOWLEDGE_EXTRACT));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireEnabled(S2Feature.KNOWLEDGE_EXTRACT));
        assertEquals(ErrorCode.E3004, ex.getErrorCode());
    }

    @Test
    void requireEnabledPassesWhenOpen() {
        when(repository.isEnabled("wrongBook")).thenReturn(true);
        new S2FeatureService(repository).requireEnabled(S2Feature.WRONG_BOOK);
    }

    @Test
    void updateRejectsUnknownKeyAndPersistsKnownKey() {
        var service = new S2FeatureService(repository);

        assertThrows(BusinessException.class, () -> service.update("noSuchFeature", true));
        var row = service.update("practice", true);
        assertEquals("practice", row.key());
        assertTrue(row.enabled());
        verify(repository).setEnabled("practice", true);
    }
}
