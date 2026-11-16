package com.ai.studenthelper.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CR-025：知识点随解答产出——解析第 2 段的「大 > 小」列表，上限 3 项，不调用模型。 */
class KnowledgeExtractorTest {

    @Test
    void parsesTwoLevelListKeepingOrder() {
        String section = """
                - 栈 > 后进先出
                - 栈 > 合法出栈序列
                二叉树 → 遍历
                """;
        List<KnowledgeExtractor.KpRef> refs = KnowledgeExtractor.parseSection(section);
        assertEquals(3, refs.size());
        assertEquals("栈", refs.get(0).major());
        assertEquals("后进先出", refs.get(0).minor());
        assertEquals("二叉树", refs.get(2).major());
        assertEquals("遍历", refs.get(2).minor());
    }

    @Test
    void capsAtThreeItems() {
        String section = """
                栈 > 后进先出
                队列 > 先进先出
                二叉树 > 遍历
                图 > 最短路径
                排序 > 稳定性
                """;
        List<KnowledgeExtractor.KpRef> refs = KnowledgeExtractor.parseSection(section);
        assertEquals(KnowledgeExtractor.MAX_ITEMS, refs.size(), "每轮最多 3 个（CR-025）");
        assertTrue(refs.stream().noneMatch(r -> "图".equals(r.major())), "第 4 项起丢弃");
    }

    @Test
    void dedupesSamePairAndDropsPlaceholders() {
        String section = """
                栈 > 后进先出
                **栈 > 后进先出**
                无
                暂无
                """;
        List<KnowledgeExtractor.KpRef> refs = KnowledgeExtractor.parseSection(section);
        assertEquals(1, refs.size());
    }

    @Test
    void longSentenceWithoutSeparatorIsDropped() {
        String section = """
                本题主要考查栈的后进先出性质以及合法出栈序列的判定方法
                栈 > 后进先出
                """;
        List<KnowledgeExtractor.KpRef> refs = KnowledgeExtractor.parseSection(section);
        assertEquals(1, refs.size());
        assertEquals("栈", refs.get(0).major());
    }

    @Test
    void colonAndBareLineFallbacks() {
        List<KnowledgeExtractor.KpRef> colon = KnowledgeExtractor.parseSection("洛必达法则：0/0 型极限");
        assertEquals("洛必达法则", colon.get(0).major());
        assertEquals("0/0 型极限", colon.get(0).minor());

        List<KnowledgeExtractor.KpRef> bare = KnowledgeExtractor.parseSection("时间复杂度");
        assertEquals("时间复杂度", bare.get(0).major());
        assertNull(bare.get(0).minor());
    }

    @Test
    void emptyOrNullSectionIsSafe() {
        assertTrue(KnowledgeExtractor.parseSection(null).isEmpty());
        assertTrue(KnowledgeExtractor.parseSection("").isEmpty());
        assertTrue(KnowledgeExtractor.parseSection("   \n  \n").isEmpty());
    }

    @Test
    void stripsListMarkersAndTrailingPunctuation() {
        assertEquals("栈 > 后进先出", KnowledgeExtractor.clean("1. 栈 > 后进先出。"));
        assertEquals("栈 > 后进先出", KnowledgeExtractor.clean("* 栈 > 后进先出；"));
        assertEquals("栈 > 后进先出", KnowledgeExtractor.clean("（2）栈 > 后进先出，"));
    }
}
