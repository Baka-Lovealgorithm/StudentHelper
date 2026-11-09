package com.ai.studenthelper.common.dto;

import java.util.List;

/**
 * T-305 学情统计。不含金额字段（沿用 FR-206 边界），也不依赖图表库，
 * 由前端用文本 + CSS 条形呈现（DD-11：图表库暂不引入）。
 */
public record LearningStatsResponse(
        int sessions,
        int questions,
        int answers,
        int failedAnswers,
        long promptTokens,
        long completionTokens,
        int avgDurationMs,
        String currentSubject,
        List<DayCount> byDay,
        List<SubjectCount> bySubject) {

    /** 最近 7 天按天的提问量。 */
    public record DayCount(String date, int count) {
    }

    /** 学科分布：来自知识点命中（未开启知识点抽取时为空列表，不伪造数据）。 */
    public record SubjectCount(String subject, int count) {
    }
}
