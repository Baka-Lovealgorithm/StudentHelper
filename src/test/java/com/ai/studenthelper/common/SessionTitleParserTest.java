package com.ai.studenthelper.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-033：会话标题标记行的解析 / 清洗 / 剥离（T-104 / AC-104-12~13）。
 */
class SessionTitleParserTest {

    @Test
    void extractsAndStripsTitleLineAtHead() {
        var result = SessionTitleParser.extract(
                "【会话标题】循环冗余校验 CRC\n\n## ① 题意\n正文");

        assertEquals("循环冗余校验 CRC", result.title());
        assertFalse(result.stripped().contains("【会话标题】"));
        assertTrue(result.stripped().startsWith("## ① 题意"), "剥离后不应留开头空行");
        assertTrue(result.stripped().endsWith("正文"));
    }

    @Test
    void extractsTitleLineAnywhereAndStripsIt() {
        var result = SessionTitleParser.extract(
                "## ① 题意\n【会话标题】：二叉树遍历\n正文");

        assertEquals("二叉树遍历", result.title());
        assertFalse(result.stripped().contains("【会话标题】"));
        assertTrue(result.stripped().contains("## ① 题意"));
        assertTrue(result.stripped().contains("正文"));
    }

    @Test
    void sanitizesQuotesPunctuationAndTruncates() {
        assertEquals("最小生成树", SessionTitleParser.parseTitleLine("【会话标题】“最小生成树”。"));
        assertEquals("Kruskal 算法", SessionTitleParser.parseTitleLine("【会话标题】 Kruskal 算法！"));
        // 16 字截断
        String longTitle = SessionTitleParser.parseTitleLine("【会话标题】一二三四五六七八九十甲乙丙丁戊己");
        assertEquals(16, longTitle.length());
        assertEquals("一二三四五六七八九十甲乙丙丁戊己", longTitle);
    }

    @Test
    void rejectsEmptyAndSymbolOnlyTitles() {
        assertNull(SessionTitleParser.parseTitleLine("【会话标题】"));
        assertNull(SessionTitleParser.parseTitleLine("【会话标题】   "));
        assertNull(SessionTitleParser.parseTitleLine("【会话标题】……"));
        assertNull(SessionTitleParser.parseTitleLine("【会话标题】。。。"));
    }

    @Test
    void noMarkerReturnsNullAndKeepsBodyIntact() {
        String body = "## ① 题意\n正文，无标题行";
        var result = SessionTitleParser.extract(body);

        assertNull(result.title());
        assertEquals(body, result.stripped(), "无标记行时正文逐字保留");
    }

    @Test
    void onlyMarkerLineLeavesEmptyBody() {
        var result = SessionTitleParser.extract("【会话标题】图的着色\n");

        assertEquals("图的着色", result.title());
        assertEquals("", result.stripped());
    }

    @Test
    void colonVariantsAreSupported() {
        assertEquals("哈夫曼编码", SessionTitleParser.parseTitleLine("【会话标题】：哈夫曼编码"));
        assertEquals("哈夫曼编码", SessionTitleParser.parseTitleLine("【会话标题】:哈夫曼编码"));
    }

    @Test
    void handlesNullAndBlankAnswer() {
        var nullResult = SessionTitleParser.extract(null);
        assertNull(nullResult.title());
        assertEquals("", nullResult.stripped());

        var blankResult = SessionTitleParser.extract("   ");
        assertNull(blankResult.title());
        assertEquals("   ", blankResult.stripped());
    }
}
