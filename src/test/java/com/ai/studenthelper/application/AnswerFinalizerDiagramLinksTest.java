package com.ai.studenthelper.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CR-047：作图工具生成后、模型漏贴链接时的兜底补图。 */
class AnswerFinalizerDiagramLinksTest {

    private static final String LINK = "![二叉树](/api/images/658/ab12.png)";
    private static final String ANSWER = """
            ## ① 题目理解
            给定二叉搜索树。

            ## ④ 详细步骤
            按左根右访问。

            ## ⑤ 最终答案
            2 → 5 → 10 → 15。
            """;

    @Test
    void keepsAnswerUntouchedWhenLinkAlreadyPresent() {
        String withLink = ANSWER.replace("按左根右访问。", "按左根右访问。\n\n" + LINK);
        assertEquals(withLink, AnswerFinalizer.ensureDiagramLinks(withLink, List.of(LINK)));
    }

    @Test
    void insertsMissingLinkBeforeFinalAnswerSection() {
        String patched = AnswerFinalizer.ensureDiagramLinks(ANSWER, List.of(LINK));
        assertTrue(patched.contains(LINK));
        assertTrue(patched.indexOf(LINK) < patched.indexOf("## ⑤ 最终答案"),
                "图应补在 ⑤ 最终答案之前");
        assertTrue(patched.indexOf(LINK) > patched.indexOf("## ④ 详细步骤"));
    }

    @Test
    void appendsLinkWhenNoStructureHeadings() {
        String plain = "这是一段没有结构标题的回答。";
        String patched = AnswerFinalizer.ensureDiagramLinks(plain, List.of(LINK));
        assertTrue(patched.startsWith(plain));
        assertTrue(patched.contains(LINK));
    }

    @Test
    void toleratesNullAndEmptyInputs() {
        assertEquals(ANSWER, AnswerFinalizer.ensureDiagramLinks(ANSWER, null));
        assertEquals(ANSWER, AnswerFinalizer.ensureDiagramLinks(ANSWER, List.of()));
        assertEquals(ANSWER, AnswerFinalizer.ensureDiagramLinks(ANSWER, List.of("  ")));
        assertFalse(AnswerFinalizer.ensureDiagramLinks("", List.of(LINK)).isEmpty());
    }
}
