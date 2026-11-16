package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.ChatCommand;
import com.ai.studenthelper.domain.Message;

/**
 * 一次问答请求的显式载体（CR-043 / 候选 02）。
 *
 * <p>改造前这些状态以局部变量散落在 {@code solve()} 的 174 行里，生成前与生成后两段
 * 只能靠「在同一个方法里」共享。现在显式打包，{@link AnswerFinalizer} 只依赖本对象
 * 与生成结果，不再需要从服务里取上下文。</p>
 *
 * @param command         本轮请求
 * @param userMessage     已落库的用户消息（成功时标 SUCCESS，失败时标 FAILED）
 * @param prepared        生成前装配结果
 * @param processRecorder 过程聚合器（生成结束后落库）
 * @param startedAt       请求开始时间戳（TTFT / duration 基准）
 */
public record ChatTurn(
        ChatCommand command,
        Message userMessage,
        PreparedTurn prepared,
        ProcessRecorder processRecorder,
        long startedAt
) {
}
