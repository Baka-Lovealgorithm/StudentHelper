package com.ai.studenthelper.application;

import com.ai.studenthelper.common.dto.DonePayload;
import com.ai.studenthelper.common.dto.ProcessItem;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.domain.enums.AgentStage;

/**
 * 解题输出端口：Application 层只描述事件，不感知 SSE / HTTP，由 Controller 适配。
 */
public interface SolutionStreamSink {

    void onStatus(AgentStage stage);

    void onDelta(String text, int index);

    /** CR-027：模型行为过程；默认空实现，记录桩 / 单测无需感知新增事件。 */
    default void onProcess(ProcessItem item) {
    }

    void onDone(DonePayload payload);

    void onError(ErrorCode code, String detail);
}
