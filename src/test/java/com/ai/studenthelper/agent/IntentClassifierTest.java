package com.ai.studenthelper.agent;

import com.ai.studenthelper.domain.enums.AnswerIntent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CR-039 / AC-506-1 / AC-506-2 / AC-506-3：三态解析、脏输出容错与降级口径。 */
class IntentClassifierTest {

    @Test
    void parsesThreeIntentsWithOptionalReason() {
        IntentClassifier.Result solve = IntentClassifier.parseOutput("SOLVE | 本轮给出新题目", 1L);
        assertEquals(AnswerIntent.SOLVE, solve.intent());
        assertEquals("本轮给出新题目", solve.reason());
        assertEquals("model", solve.source());

        assertEquals(AnswerIntent.FOLLOW_UP,
                IntentClassifier.parseOutput("FOLLOW_UP | 追问上一步为什么成立", 1L).intent());
        assertEquals(AnswerIntent.EXPLAIN,
                IntentClassifier.parseOutput("EXPLAIN | 询问概念定义与区别", 1L).intent());
    }

    /** 模型可能带前后空白、大小写或额外换行，仍应识别；非法词一律回落 SOLVE。 */
    @Test
    void toleratesDirtyOutputAndFallsBackToSolve() {
        assertEquals(AnswerIntent.EXPLAIN, IntentClassifier.parseOutput("  explain  ", 1L).intent());
        assertEquals(AnswerIntent.FOLLOW_UP,
                IntentClassifier.parseOutput("follow_up\n理由：追问", 1L).intent());
        assertEquals(AnswerIntent.SOLVE, IntentClassifier.parseOutput("我觉得这是解题", 1L).intent());
        assertEquals(AnswerIntent.SOLVE, IntentClassifier.parseOutput("", 1L).intent());
        assertEquals(AnswerIntent.SOLVE, IntentClassifier.parseOutput(null, 1L).intent());
    }

    /** 理由截断到 40 字，且不把整段输出当理由。 */
    @Test
    void reasonIsTrimmedToFortyChars() {
        String longReason = "很长的理由".repeat(20);
        IntentClassifier.Result result = IntentClassifier.parseOutput("EXPLAIN | " + longReason, 1L);
        assertTrue(result.reason().length() <= 40);
    }

    /** AC-506-2：缺少模型配置时不发起调用，直接回落 SOLVE 并标记 fallback。 */
    @Test
    void missingModelConfigFallsBackWithoutCallingModel() {
        IntentClassifier classifier = new IntentClassifier(null); // 未配置模型时不会用到工厂
        IntentClassifier.Result result = classifier.classify(9L, List.of(), "什么是栈？", 0, null);
        assertEquals(AnswerIntent.SOLVE, result.intent());
        assertTrue(result.fallback());
    }
}
