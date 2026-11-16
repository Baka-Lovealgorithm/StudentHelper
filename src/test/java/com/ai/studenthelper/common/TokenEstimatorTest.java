package com.ai.studenthelper.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** TCR-040 / AC-401-9：token 估算口径 {@code token ≈ ceil(字符数 / 1.5)}。 */
class TokenEstimatorTest {

    @Test
    void nullAndEmptyAreZero() {
        assertEquals(0, TokenEstimator.estimate(null));
        assertEquals(0, TokenEstimator.estimate(""));
    }

    @Test
    void shortChineseRoundsUp() {
        assertEquals(1, TokenEstimator.estimate("栈"));          // ceil(1/1.5) = 1
        assertEquals(2, TokenEstimator.estimate("栈和"));        // ceil(2/1.5) = 2
        assertEquals(2, TokenEstimator.estimate("栈和队"));      // ceil(3/1.5) = 2
        assertEquals(3, TokenEstimator.estimate("栈和队列"));    // ceil(4/1.5) = 3
    }

    @Test
    void longChineseScalesLinearly() {
        String text = "题".repeat(825); // 项目单条消息平均字符数
        assertEquals(550, TokenEstimator.estimate(text));
        assertEquals(1100, TokenEstimator.estimate(text.repeat(2)));
    }

    @Test
    void estimateAllSkipsNullAndSums() {
        List<String> texts = Arrays.asList("栈和队列", null, "", "题".repeat(825));
        assertEquals(3 + 550, TokenEstimator.estimateAll(texts));
        assertEquals(0, TokenEstimator.estimateAll(null));
    }

    @Test
    void thresholdBoundaryMatchesDefaultTwelveThousand() {
        // 18000 字符正好是 12000 token，是「不超阈值」的右边界
        assertEquals(12000, TokenEstimator.estimate("字".repeat(18000)));
        assertEquals(12001, TokenEstimator.estimate("字".repeat(18001)));
    }
}
