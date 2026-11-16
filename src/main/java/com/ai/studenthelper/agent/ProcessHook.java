package com.ai.studenthelper.agent;

import com.ai.studenthelper.application.ProcessRecorder;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.ReasoningChunkEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * CR-027：从 AgentScope Hook 采集模型行为，避免解析流式 `tool_calls`（TCR-023 教训）。
 * 采集失败只记日志，绝不改变事件对象或中断解题链路。
 */
public class ProcessHook implements Hook {

    private static final Logger log = LoggerFactory.getLogger(ProcessHook.class);

    private final ProcessRecorder recorder;

    public ProcessHook(ProcessRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        try {
            if (event instanceof PostActingEvent acting && acting.getToolUse() != null) {
                recorder.recordTool(acting.getToolUse().getName(),
                        acting.getToolUse().getInput(),
                        textOf(acting.getToolResult()));
            } else if (event instanceof ReasoningChunkEvent reasoning) {
                recorder.recordThinking(textOf(reasoning.getIncrementalChunk()));
            }
        } catch (Exception e) {
            log.warn("PROCESS_HOOK 采集失败 reason={}", e.getMessage());
        }
        return Mono.just(event);
    }

    /** 工具返回可能是 TextBlock 列表；只取文本，忽略图片 / 结构化块。 */
    private static String textOf(ToolResultBlock result) {
        if (result == null || result.getOutput() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : result.getOutput()) {
            if (block instanceof TextBlock text && text.getText() != null) {
                sb.append(text.getText());
            }
        }
        return sb.toString();
    }

    /** 思考增量可能落在 ThinkingBlock 或 TextBlock，两者都取。 */
    private static String textOf(Msg msg) {
        if (msg == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        List<ThinkingBlock> thinking = msg.getContentBlocks(ThinkingBlock.class);
        for (ThinkingBlock block : thinking) {
            if (block.getThinking() != null) {
                sb.append(block.getThinking());
            }
        }
        List<TextBlock> text = msg.getContentBlocks(TextBlock.class);
        for (TextBlock block : text) {
            if (block.getText() != null) {
                sb.append(block.getText());
            }
        }
        return sb.toString();
    }
}
