package com.ai.studenthelper.application;

import com.ai.studenthelper.domain.enums.S2Feature;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CR-043 / AC-508-6：请求级开关快照——一次读取、整轮一致。 */
class FeatureSnapshotTest {

    @Test
    void snapshotReflectsFlagsAndDefaultsToOff() {
        FeatureSnapshot snapshot = new FeatureSnapshot(Map.of(
                "knowledgeExtract", true,
                "historyImage", true,
                "multiSolution", false));

        assertTrue(snapshot.enabled(S2Feature.KNOWLEDGE_EXTRACT));
        assertTrue(snapshot.enabled("historyImage"));
        assertFalse(snapshot.enabled(S2Feature.MULTI_SOLUTION), "显式关闭");
        assertFalse(snapshot.enabled(S2Feature.MCP), "缺行按关闭处理（与 t_feature_toggle 口径一致）");
        assertFalse(snapshot.enabled((S2Feature) null), "null 开关不得抛错");
        assertFalse(FeatureSnapshot.empty().enabled(S2Feature.MEMORY));
    }

    /** 同一快照被反复读取时结果不变——改造前每次 isEnabled 都可能读到库里新值。 */
    @Test
    void repeatedReadsAreStableWithinOneSnapshot() {
        FeatureSnapshot snapshot = new FeatureSnapshot(Map.of("memory", true));
        for (int i = 0; i < 5; i++) {
            assertTrue(snapshot.enabled(S2Feature.MEMORY));
        }
        FeatureSnapshot reloaded = new FeatureSnapshot(Map.of("memory", false));
        assertFalse(reloaded.enabled(S2Feature.MEMORY), "新快照反映最新状态，互不影响");
    }

    /** 知识点开关前移到 plan：开关关闭时即使形态是解题也不抽。 */
    @Test
    void knowledgeExtractionHonoursSnapshot() {
        SubjectSkillRuntime runtime = org.mockito.Mockito.mock(SubjectSkillRuntime.class);
        AnswerPlan on = AnswerPlan.build(com.ai.studenthelper.domain.enums.AnswerIntent.SOLVE,
                com.ai.studenthelper.domain.enums.GuideMode.FULL, "p", false, false, false, runtime,
                new FeatureSnapshot(Map.of("knowledgeExtract", true)));
        AnswerPlan off = AnswerPlan.build(com.ai.studenthelper.domain.enums.AnswerIntent.SOLVE,
                com.ai.studenthelper.domain.enums.GuideMode.FULL, "p", false, false, false, runtime,
                new FeatureSnapshot(Map.of("knowledgeExtract", false)));

        assertTrue(on.extractKnowledge());
        assertFalse(off.extractKnowledge(), "knowledgeExtract 关闭时 plan 不再要求抽知识点");
    }
}
