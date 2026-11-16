package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.ChatCommand;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import io.agentscope.core.message.Msg;

import java.util.List;

/**
 * 一次「生成前装配」的结果（CR-043 / 候选 02）。
 *
 * @param config   本轮解析出的模型配置
 * @param plan     本轮形态的完整决策
 * @param messages 已装配好的上下文消息
 * @param runtime  学科运行时（供工具、标签、审计码复用）
 */
public record PreparedTurn(
        ModelConfigResolver.ResolvedModelConfig config,
        AnswerPlan plan,
        List<Msg> messages,
        SubjectSkillRuntime runtime
) {
}
