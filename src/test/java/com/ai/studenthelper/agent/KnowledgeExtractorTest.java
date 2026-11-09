package com.ai.studenthelper.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DD-02：抽取解析、两级结构与防幻觉过滤，不调用真实模型。 */
class KnowledgeExtractorTest {

    @Test
    void sectionKnowledgeExtractsOnlySectionTwo() {
        String answer = """
                ## ① 题目理解
                复述题意
                ## ② 涉及知识点
                栈的性质、栈的顺序存储、二叉树的遍历
                ## ③ 解题思路
                分析
                """;
        String section = KnowledgeExtractor.sectionKnowledge(answer);
        assertEquals("栈的性质、栈的顺序存储、二叉树的遍历", section);
    }

    @Test
    void parseRefsAcceptsTwoLevelJsonAndDropsFencedWrapper() {
        String section = "栈的性质、栈的顺序存储";
        String raw = "```json\n[{\"major\":\"栈\",\"minor\":\"栈的性质\"},"
                + "{\"major\":\"栈\",\"minor\":\"栈的顺序存储\"}]\n```";
        List<KnowledgeExtractor.KpRef> refs = KnowledgeExtractor.parseRefs(raw, section);
        assertEquals(2, refs.size());
        assertEquals("栈", refs.get(0).major());
        assertEquals("栈的性质", refs.get(0).minor());
    }

    @Test
    void parseRefsDropsHallucinatedAndInvalidItems() {
        String section = "栈的性质";
        String raw = "[{\"major\":\"栈\",\"minor\":\"栈的性质\"},"
                + "{\"major\":\"图论\",\"minor\":\"最短路径\"},"
                + "{\"major\":null,\"minor\":\"x\"}]";
        List<KnowledgeExtractor.KpRef> refs = KnowledgeExtractor.parseRefs(raw, section);
        assertEquals(1, refs.size(), "原文未出现的知识点必须丢弃（NFR-104）");
        assertEquals("栈", refs.get(0).major());
    }

    @Test
    void malformedJsonOrEmptyArrayIsSafe() {
        assertTrue(KnowledgeExtractor.parseRefs("not json", "栈").isEmpty());
        assertTrue(KnowledgeExtractor.parseRefs("[]", "栈").isEmpty());
        assertTrue(KnowledgeExtractor.parseRefs(null, "栈").isEmpty());
    }
}
