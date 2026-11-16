package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.KnowledgeExtractor;
import com.ai.studenthelper.agent.SolutionSectionParser;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.ChatCommand;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.AnswerIntent;
import com.ai.studenthelper.domain.enums.MessageStatus;
import com.ai.studenthelper.infra.log.CallLogRecorder;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.ProcessRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** CR-053 / AC-518-2：停止轮次落库 FAILED / E1006 中断消息。 */
class AnswerFinalizerInterruptedTest {

    @Test
    void persistsPartialAnswerAsFailedE1006AndEmitsErrorWithoutDone() {
        MessageRepository messages = mock(MessageRepository.class);
        when(messages.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        ProcessRepository processes = mock(ProcessRepository.class);
        CallLogRecorder logs = mock(CallLogRecorder.class);
        SolutionStreamSink sink = mock(SolutionStreamSink.class);

        AnswerFinalizer finalizer = new AnswerFinalizer(messages,
                mock(SessionApplicationService.class), mock(SolutionSectionParser.class),
                logs, mock(KnowledgeExtractor.class), processes);

        ChatCommand command = new ChatCommand(7L, "题目：栈与队列", null, 0, List.of());
        Message user = new Message();
        user.setId(1L);
        user.setStatus(MessageStatus.PENDING.name());

        AnswerPlan plan = mock(AnswerPlan.class);
        when(plan.intent()).thenReturn(AnswerIntent.SOLVE);
        SubjectSkillRuntime runtime = mock(SubjectSkillRuntime.class);
        when(runtime.auditHit()).thenReturn("");
        ModelConfigResolver.ResolvedModelConfig config = mock(ModelConfigResolver.ResolvedModelConfig.class);
        when(config.id()).thenReturn(3L);
        when(config.debug()).thenReturn(false);
        PreparedTurn prepared = mock(PreparedTurn.class);
        when(prepared.plan()).thenReturn(plan);
        when(prepared.config()).thenReturn(config);
        when(prepared.runtime()).thenReturn(runtime);

        ProcessRecorder recorder = new ProcessRecorder(sink);
        ChatTurn turn = new ChatTurn(command, user, prepared, recorder, System.currentTimeMillis());

        Message saved = finalizer.finalizeInterrupted(turn,
                "【会话标题】栈与队列辨析\n## ① 题目理解\n半截正文", sink);

        assertEquals(MessageStatus.FAILED.name(), saved.getStatus());
        assertEquals(ErrorCode.E1006.getCode(), saved.getErrorCode());
        assertEquals("## ① 题目理解\n半截正文", saved.getContent(), "剥离标题行、不追加免责声明");
        assertEquals(MessageStatus.SUCCESS.name(), user.getStatus(), "用户题面保持 SUCCESS");
        verify(processes).saveAll(any(), anyList());
        verify(sink).onError(eq(ErrorCode.E1006), isNull());
        verify(sink, never()).onDone(any());
        verify(logs).record(any(), eq(config), eq(0), eq(0), anyLong(), eq("FAILED"),
                eq(ErrorCode.E1006.getCode()), anyBoolean(), anyString());

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messages, atLeast(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(m -> ErrorCode.E1006.getCode().equals(m.getErrorCode())));
    }
}
