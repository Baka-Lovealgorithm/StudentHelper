package com.ai.studenthelper.common.dto;

import java.util.List;

/**
 * T-305 学情统计。**CR-023 起不再返回 token 与耗时**（token / 耗时只归用量看板，避免两个页面职责重叠），
 * 也不含金额字段（沿用 FR-206 边界）；仍不依赖图表库，由前端用 CSS 竖柱呈现（DD-11）。
 *
 * @param days      统计窗口：0 = 全部，1 / 3 / 7 = 最近 N 个自然日（含今天）
 * @param byDay     按天三指标，窗口为 1 / 3 / 7 时逐日补齐缺失日期（计 0）
 * @param bySubject 学科分布，来自知识点命中；未开启知识点抽取时为空列表，不伪造数据
 */
public record LearningStatsResponse(
        int days,
        int sessions,
        int questions,
        int answers,
        int failedAnswers,
        String currentSubject,
        List<DayStat> byDay,
        List<SubjectCount> bySubject) {

    /** 按天三个可切换指标：提问量 / 成功解答 / 失败轮次。 */
    public record DayStat(String date, int questions, int answers, int failed) {
    }

    /** 学科分布：来自知识点命中（未开启知识点抽取时为空列表，不伪造数据）。 */
    public record SubjectCount(String subject, int count) {
    }
}
