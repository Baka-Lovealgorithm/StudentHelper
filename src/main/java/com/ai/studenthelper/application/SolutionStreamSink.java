package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.DonePayload;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.enums.AgentStage;

/**
 * 解题输出端口：Application 层只描述事件，不感知 SSE / HTTP，由 Controller 适配。
 */
public interface SolutionStreamSink {

    void onStatus(AgentStage stage);

    void onDelta(String text, int index);

    void onDone(DonePayload payload);

    void onError(ErrorCode code, String detail);
}
