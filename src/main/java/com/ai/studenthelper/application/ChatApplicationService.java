package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.ChatContextAssembler;
import com.ai.studenthelper.agent.KnowledgeExtractor;
import com.ai.studenthelper.agent.SolutionSectionParser;
import com.ai.studenthelper.agent.SolverAgentFactory;
import com.ai.studenthelper.agent.SolverPromptTemplate;
import com.ai.studenthelper.agent.S2PromptAugmenter;
import com.ai.studenthelper.common.dto.ChatCommand;
import com.ai.studenthelper.common.dto.DonePayload;
import com.ai.studenthelper.common.dto.ImagePayload;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.config.AppProperties;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.AgentStage;
import com.ai.studenthelper.domain.enums.GuideMode;
import com.ai.studenthelper.domain.enums.MessageRole;
import com.ai.studenthelper.domain.enums.MessageStatus;
import com.ai.studenthelper.infra.log.CallLogRecorder;
import com.ai.studenthelper.infra.log.RawAnswerArchiver;
import com.ai.studenthelper.infra.model.AgentErrorClassifier;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.storage.ImageStorage;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 解题主流程编排：校验 → 落用户消息 → 动态装配 Agent → 流式推送 → 落库 → done（UC-01）。 */
@Service
public class ChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);

    private final SessionApplicationService sessionService;
    private final MessageRepository messageRepository;
    private final ImageStorage imageStorage;
    private final ModelConfigResolver configResolver;
    private final SolverAgentFactory agentFactory;
    private final ChatContextAssembler contextAssembler;
    private final SolutionSectionParser parser;
    private final CallLogRecorder logRecorder;
    private final RawAnswerArchiver rawAnswerArchiver;
    private final KnowledgeExtractor knowledgeExtractor;
    private final AppProperties properties;
    private final S2PromptAugmenter promptAugmenter;

    public ChatApplicationService(SessionApplicationService sessionService,
                                  MessageRepository messageRepository,
                                  ImageStorage imageStorage,
                                  ModelConfigResolver configResolver,
                                  SolverAgentFactory agentFactory,
                                  ChatContextAssembler contextAssembler,
                                  SolutionSectionParser parser,
                                  CallLogRecorder logRecorder,
                                  RawAnswerArchiver rawAnswerArchiver,
                                  KnowledgeExtractor knowledgeExtractor,
                                  AppProperties properties,
                                  S2PromptAugmenter promptAugmenter) {
        this.sessionService = sessionService;
        this.messageRepository = messageRepository;
        this.imageStorage = imageStorage;
        this.configResolver = configResolver;
        this.agentFactory = agentFactory;
        this.contextAssembler = contextAssembler;
        this.parser = parser;
        this.logRecorder = logRecorder;
        this.rawAnswerArchiver = rawAnswerArchiver;
        this.knowledgeExtractor = knowledgeExtractor;
        this.properties = properties;
        this.promptAugmenter = promptAugmenter;
    }

    public void solve(ChatCommand command, SolutionStreamSink sink) {
        long t0 = System.currentTimeMillis();
        Message userMessage = null;
        ModelConfigResolver.ResolvedModelConfig config = null;
        Toolkit toolkit = null;
        String generatedText = null;
        try {
            sessionService.requireSession(command.sessionId());
            validate(command);
            config = configResolver.resolveForSession(command.sessionId());

            List<String> paths = new ArrayList<>();
            for (ImagePayload image : command.images()) {
                paths.add(imageStorage.save(command.sessionId(), image.bytes(), image.extension()));
            }
            userMessage = newUserMessage(command, paths);
            userMessage = messageRepository.save(userMessage);

            // 先落库再校验视觉能力：失败轮次也要在历史中标记 FAILED（技术规范 2.4 / AC-110-4）
            if (!command.images().isEmpty() && !config.visionSupported()) {
                throw new BusinessException(ErrorCode.E2004);
            }

            sink.onStatus(AgentStage.UNDERSTANDING);
            GuideMode mode = GuideMode.fromCode(command.guideMode());
            // 关闭态 buildAndAugment 输出与 S1 逐字一致（AC-204-1）；
            // 开启态按当前学科构建 base 并依次叠加记忆 / 补充段落 / 多解法 / 技能
            String systemPrompt = promptAugmenter.buildAndAugment(mode);
            List<Message> history = messageRepository.findBySessionId(command.sessionId());
            List<Msg> messages = contextAssembler.assemble(history, command.parts(),
                    command.text(), command.hint(), command.images());
            // T-301：MCP 开关开启且有可用 Server 时才装配工具；null 表示无工具
            toolkit = agentFactory.openToolkit();
            var agent = agentFactory.create(systemPrompt, config, toolkit);

            sink.onStatus(AgentStage.GENERATING);
            Generation generation = generate(agent, messages, sink, mode, t0);
            String answer = generation.text();
            generatedText = answer;

            parser.parse(answer, mode);
            String stored = parser.appendDisclaimer(answer);

            Message assistant = newAssistantMessage(command, config, stored,
                    generation.promptTokens(), generation.completionTokens(), 0L);
            Message savedAssistant = messageRepository.save(assistant);
            userMessage.setStatus(MessageStatus.SUCCESS.name());
            messageRepository.save(userMessage);

            // t2 = 落库完成；t_message 与 t_call_log 共用同一 durationMs，done 在两者之后发送
            long durationMs = System.currentTimeMillis() - t0;
            savedAssistant.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
            messageRepository.save(savedAssistant);
            logRecorder.record(savedAssistant.getId(), config, generation.promptTokens(),
                    generation.completionTokens(), durationMs, "SUCCESS", null);
            log.info("CHAT_TIMING sessionId={} messageId={} ttftMs={} totalMs={} model={}",
                    command.sessionId(), savedAssistant.getId(), generation.ttftMs(),
                    durationMs, config.modelName());

            sink.onDone(new DonePayload(savedAssistant.getId(), generation.promptTokens(),
                    generation.completionTokens(), durationMs, generation.ttftMs()));

            // DD-02：知识点抽取在 done 之后执行，不计入 ttftMs / durationMs；
            // 内部吞掉所有异常，绝不因 S2 功能让解题失败（Gate-2 关闭态/开启态一致）。
            knowledgeExtractor.extractAndRecord(command.sessionId(), savedAssistant.getId(), answer);
        } catch (Exception e) {
            handleFailure(command, sink, userMessage, config, e, t0, generatedText);
        } finally {
            // 工具连接必须随请求关闭，避免 MCP 客户端泄漏
            agentFactory.closeToolkit(toolkit);
        }
    }

    private Generation generate(io.agentscope.core.ReActAgent agent, List<Msg> messages,
                                SolutionStreamSink sink, GuideMode mode, long t0) {
        AtomicReference<String> accumulated = new AtomicReference<>("");
        AtomicInteger index = new AtomicInteger();
        AtomicLong firstDeltaAt = new AtomicLong(0);
        AtomicReference<Msg> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        StreamOptions options = StreamOptions.builder()
                .eventTypes(EventType.ALL)
                .incremental(false)
                .includeReasoningChunk(true)
                .includeSummaryChunk(true)
                .build();

        agent.stream(messages, options)
                .timeout(Duration.ofMillis(properties.getChat().getHardTimeoutMs()))
                .subscribe(event -> onEvent(event, accumulated, index, result, sink, mode, firstDeltaAt, t0),
                        error -> { failure.set(error); latch.countDown(); },
                        latch::countDown);

        try {
            if (!latch.await(properties.getChat().getSseTimeoutMs(), TimeUnit.MILLISECONDS)) {
                failure.compareAndSet(null, new java.util.concurrent.TimeoutException("SSE wait timed out"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, e);
        }
        if (failure.get() != null) {
            throw new IllegalStateException("MODEL_CALL_FAILED", failure.get());
        }

        Msg finalMsg = result.get();
        String full = trimForMode(finalMsg == null ? null : finalMsg.getTextContent(), mode);
        String text = accumulated.get();
        if (full != null && full.length() > text.length() && full.startsWith(text)) {
            String tail = full.substring(text.length());
            sink.onDelta(tail, index.getAndIncrement());
            markFirstDelta(firstDeltaAt, t0);
            text = full;
        }
        if (text.isBlank()) {
            text = full == null ? "" : full;
            if (!text.isBlank()) {
                sink.onDelta(text, index.getAndIncrement());
                markFirstDelta(firstDeltaAt, t0);
            }
        }
        ChatUsage usage = finalMsg == null ? null : finalMsg.getChatUsage();
        int prompt = usage == null ? 0 : usage.getInputTokens();
        int completion = usage == null ? 0 : usage.getOutputTokens();
        return new Generation(text, prompt, completion, firstDeltaAt.get());
    }

    /** 前缀差分：无论框架给累积文本还是增量，都能推出不重复的 delta。 */
    private void onEvent(Event event, AtomicReference<String> accumulated, AtomicInteger index,
                         AtomicReference<Msg> result, SolutionStreamSink sink, GuideMode mode,
                         AtomicLong firstDeltaAt, long t0) {
        Msg msg = event.getMessage();
        if (msg == null) {
            return;
        }
        if (event.getType() == EventType.AGENT_RESULT) {
            result.set(msg);
            return;
        }
        String chunk = trimForMode(msg.getTextContent(), mode);
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        String previous = accumulated.get();
        if (chunk.length() > previous.length() && chunk.startsWith(previous)) {
            sink.onDelta(chunk.substring(previous.length()), index.getAndIncrement());
            markFirstDelta(firstDeltaAt, t0);
            accumulated.set(chunk);
        } else if (previous.isEmpty()) {
            sink.onDelta(chunk, index.getAndIncrement());
            markFirstDelta(firstDeltaAt, t0);
            accumulated.set(chunk);
        }
    }

    private void handleFailure(ChatCommand command, SolutionStreamSink sink, Message userMessage,
                               ModelConfigResolver.ResolvedModelConfig config, Exception exception,
                               long t0, String rawAnswer) {
        Throwable root = unwrap(exception);
        ErrorCode code = exception instanceof BusinessException be
                ? be.getErrorCode()
                : AgentErrorClassifier.classify(root);
        final String detail;
        if (exception instanceof BusinessException be) {
            detail = be.getDetail() != null ? be.getDetail() : code.getDefaultMessage();
        } else {
            detail = AgentErrorClassifier.safeDetail(root, config == null ? null : config.baseUrl());
        }
        log.warn("解题失败 code={} sessionId={} reason={}", code.getCode(), command.sessionId(), detail);

        // FR-110：失败反馈优先。先发 error，后续任何落库异常都不得阻断它（TCR-014 修复 D 第 1 条）。
        // 改动前 logRecorder.record 在前，DB 异常会让 error 永远发不出去，且异步线程异常被吞。
        try {
            sink.onError(code, detail);
        } catch (Exception sinkFailure) {
            log.warn("失败事件推送异常 sessionId={} reason={}",
                    command.sessionId(), sinkFailure.getMessage());
        }

        // D-01：格式异常时把模型原始输出归档为 FAILED 消息，供调试；归档失败不掩盖原错误
        if (code == ErrorCode.E2005) {
            try {
                rawAnswerArchiver.archive(command.sessionId(), command.guideMode(), rawAnswer, code);
            } catch (Exception archiveFailure) {
                log.warn("原始输出归档失败 sessionId={} reason={}",
                        command.sessionId(), archiveFailure.getMessage());
            }
        }

        if (userMessage != null) {
            try {
                userMessage.setStatus(MessageStatus.FAILED.name());
                userMessage.setErrorCode(code.getCode());
                messageRepository.save(userMessage);
            } catch (Exception saveFailure) {
                log.warn("失败状态落库异常 sessionId={} messageId={} reason={}",
                        command.sessionId(), userMessage.getId(), saveFailure.getMessage());
            }
        }
        try {
            logRecorder.record(userMessage == null ? null : userMessage.getId(), config, 0, 0,
                    System.currentTimeMillis() - t0, "FAILED", code.getCode() + ": " + detail);
        } catch (Exception logFailure) {
            log.warn("调用日志落库异常 sessionId={} reason={}",
                    command.sessionId(), logFailure.getMessage());
        }
    }

    /** 仅思路模式：后端强制裁掉 ④ 及之后内容，不依赖模型自觉遵守（AC-108-1）。 */
    private static String trimForMode(String text, GuideMode mode) {
        if (text == null || mode != GuideMode.IDEA_ONLY) {
            return text;
        }
        int cut = text.indexOf(SolverPromptTemplate.FULL_TITLES.get(3));
        return cut >= 0 ? text.substring(0, cut).trim() + "\n" : text;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private void validate(ChatCommand command) {
        boolean hasText = command.text() != null && !command.text().isBlank();
        if (!hasText && command.images().isEmpty()) {
            throw new BusinessException(ErrorCode.E1001);
        }
        if (command.images().size() > properties.getChat().getMaxImagesPerMessage()) {
            throw new BusinessException(ErrorCode.E1002, "单条消息最多 " + properties.getChat().getMaxImagesPerMessage() + " 张图片");
        }
    }

    private Message newUserMessage(ChatCommand command, List<String> paths) {
        Message message = new Message();
        message.setSessionId(command.sessionId());
        message.setRole(MessageRole.USER.name());
        message.setContent(command.text());
        // AC-109-3：本轮难度 / 讲解要求原文随该轮消息落库，可在历史里查看
        message.setHint(command.hint() == null || command.hint().isBlank() ? null : command.hint().trim());
        message.setImagePaths(paths.isEmpty() ? null : String.join(",", paths));
        message.setGuideMode(command.guideMode());
        message.setStatus(MessageStatus.PENDING.name());
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private Message newAssistantMessage(ChatCommand command,
                                        ModelConfigResolver.ResolvedModelConfig config,
                                        String content, int promptTokens, int completionTokens,
                                        long durationMs) {
        Message message = new Message();
        message.setSessionId(command.sessionId());
        message.setRole(MessageRole.ASSISTANT.name());
        message.setContent(content);
        message.setModelConfigId(config.id());
        message.setGuideMode(command.guideMode());
        message.setStatus(MessageStatus.SUCCESS.name());
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    /** 记录首个可见字符相对 t0 的耗时（TTFT）。 */
    private static void markFirstDelta(AtomicLong firstDeltaAt, long t0) {
        firstDeltaAt.compareAndSet(0, System.currentTimeMillis() - t0);
    }

    private record Generation(String text, int promptTokens, int completionTokens, long ttftMs) {
    }
}
