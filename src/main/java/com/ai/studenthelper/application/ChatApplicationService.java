package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.DiagramTools;
import com.ai.studenthelper.agent.ProcessHook;
import com.ai.studenthelper.agent.SkillTools;
import com.ai.studenthelper.agent.SolverAgentFactory;
import com.ai.studenthelper.agent.SubjectSkillTools;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.ChatCommand;
import com.ai.studenthelper.common.dto.ImagePayload;
import com.ai.studenthelper.config.AppProperties;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.Session;
import com.ai.studenthelper.domain.enums.AgentStage;
import com.ai.studenthelper.domain.enums.MessageRole;
import com.ai.studenthelper.domain.enums.MessageStatus;
import com.ai.studenthelper.infra.log.CallLogRecorder;
import com.ai.studenthelper.infra.log.RawAnswerArchiver;
import com.ai.studenthelper.infra.model.AgentErrorClassifier;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.render.DiagramRenderer;
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
import reactor.core.Disposable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 解题主流程编排：校验 → 落用户消息 → 装配（{@link ChatTurnPreparer}）→ 流式生成 →
 * 收尾（{@link AnswerFinalizer}）（UC-01）。
 *
 * <p>CR-043：改造前 {@code solve()} 174 行、按时间顺序串 16 段职责，并靠 13 参 / 17 参双构造
 * 与 8 处 {@code null} 旁路支持「退化配置」。现在生成前与生成后各自独立成模块，
 * 本类只保留请求生命周期（校验、落用户消息、工具注册、流式推送、失败处理）。</p>
 */
@Service
public class ChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);

    private final SessionApplicationService sessionService;
    private final MessageRepository messageRepository;
    private final ImageStorage imageStorage;
    private final ModelConfigResolver configResolver;
    private final SolverAgentFactory agentFactory;
    private final RawAnswerArchiver rawAnswerArchiver;
    private final CallLogRecorder logRecorder;
    private final AppProperties properties;
    private final SubjectSkillApplicationService subjectSkills;
    private final SkillApplicationService skillService;
    private final ChatTurnPreparer preparer;
    private final AnswerFinalizer finalizer;
    private final DiagramRenderer diagramRenderer;

    public ChatApplicationService(SessionApplicationService sessionService,
                                  MessageRepository messageRepository,
                                  ImageStorage imageStorage,
                                  ModelConfigResolver configResolver,
                                  SolverAgentFactory agentFactory,
                                  RawAnswerArchiver rawAnswerArchiver,
                                  CallLogRecorder logRecorder,
                                  AppProperties properties,
                                  SubjectSkillApplicationService subjectSkills,
                                  SkillApplicationService skillService,
                                  ChatTurnPreparer preparer,
                                  AnswerFinalizer finalizer,
                                  DiagramRenderer diagramRenderer) {
        this.sessionService = sessionService;
        this.messageRepository = messageRepository;
        this.imageStorage = imageStorage;
        this.configResolver = configResolver;
        this.agentFactory = agentFactory;
        this.rawAnswerArchiver = rawAnswerArchiver;
        this.logRecorder = logRecorder;
        this.properties = properties;
        this.subjectSkills = subjectSkills;
        this.skillService = skillService;
        this.preparer = preparer;
        this.finalizer = finalizer;
        this.diagramRenderer = diagramRenderer;
    }

    /** 兼容既有调用与测试：不带注册表句柄的普通生成。 */
    public void solve(ChatCommand command, SolutionStreamSink sink) {
        solve(command, sink, new GenerationCancellation());
    }

    /** CR-053：带取消句柄的生成；由 Controller 注册到 ActiveGenerationRegistry。 */
    public void solve(ChatCommand command, SolutionStreamSink sink,
                      GenerationCancellation cancellation) {
        long t0 = System.currentTimeMillis();
        // CR-027：每请求一个过程聚合器，Hook 与 SSE 共用同一份快照
        ProcessRecorder processRecorder = new ProcessRecorder(sink);
        Message userMessage = null;
        ModelConfigResolver.ResolvedModelConfig config = null;
        Toolkit toolkit = null;
        String generatedText = null;
        try {
            Session session = sessionService.requireSession(command.sessionId());
            validate(command);
            config = configResolver.resolveForSession(command.sessionId());

            List<String> paths = new ArrayList<>();
            for (ImagePayload image : command.images()) {
                paths.add(imageStorage.save(command.sessionId(), image.bytes(), image.extension()));
            }
            userMessage = messageRepository.save(newUserMessage(command, paths));

            // 先落库再校验视觉能力：失败轮次也要在历史中标记 FAILED（技术规范 2.4 / AC-110-4）
            if (!command.images().isEmpty() && !config.visionSupported()) {
                throw new BusinessException(ErrorCode.E2004);
            }

            sink.onStatus(AgentStage.UNDERSTANDING);
            PreparedTurn prepared = preparer.prepare(command, session, userMessage, config);
            AnswerPlan plan = prepared.plan();

            toolkit = agentFactory.openToolkit();
            // CR-039：追问轮不注册工具（自由问答）；解题 / 讲解都注册
            if (plan.needsSubjectTools() || plan.needsSkillTools()) {
                if (toolkit == null) {
                    toolkit = new Toolkit();
                }
                if (plan.needsSubjectTools()) {
                    // 每请求独立实例，绑定本请求的 runtime 与形态，避免并发串台
                    toolkit.registerTool(new SubjectSkillTools(subjectSkills, prepared.runtime(),
                            plan.intent()));
                }
                if (plan.needsSkillTools()) {
                    // CR-038：命中即把技能 id 写回会话，从下一轮起改为每轮注入正文
                    toolkit.registerTool(new SkillTools(skillService,
                            skillId -> sessionService.bindSkill(command.sessionId(), skillId)));
                }
            }
            // CR-047：作图工具。渲染器找不到浏览器时不注册，模型自然不会画图；
            // 成功生成的图片链接回写过程聚合器，收尾时用于「模型漏贴链接」的兜底。
            if (plan.needsDiagramTool() && diagramRenderer.available()) {
                if (toolkit == null) {
                    toolkit = new Toolkit();
                }
                toolkit.registerTool(new DiagramTools(diagramRenderer, imageStorage,
                        command.sessionId(), processRecorder::addDiagramLink));
            }
            var agent = agentFactory.create(plan.systemPrompt(), config, toolkit, plan.maxIters(),
                    new ProcessHook(processRecorder));

            sink.onStatus(AgentStage.GENERATING);
            Generation generation = generate(agent, prepared.messages(), sink, plan, t0, cancellation);
            generatedText = generation.text();

            ChatTurn turn = new ChatTurn(command, userMessage, prepared, processRecorder, t0);
            if (cancellation.isCancelled()) {
                // CR-053：用户主动停止——落库半截正文为 FAILED / E1006 中断消息，以 error 事件收尾
                finalizer.finalizeInterrupted(turn, generation.text(), sink);
            } else {
                finalizer.finalize(turn, generation.text(), generation.promptTokens(),
                        generation.completionTokens(), generation.cacheHitTokens(),
                        generation.cacheMissTokens(), generation.ttftMs(), sink);
            }
        } catch (Exception e) {
            handleFailure(command, sink, userMessage, config, e, t0, generatedText);
        } finally {
            // 工具连接必须随请求关闭，避免 MCP 客户端泄漏
            agentFactory.closeToolkit(toolkit);
        }
    }

    private Generation generate(io.agentscope.core.ReActAgent agent, List<Msg> messages,
                                SolutionStreamSink sink, AnswerPlan plan, long t0,
                                GenerationCancellation cancellation) {
        AtomicReference<String> accumulated = new AtomicReference<>("");
        AtomicInteger index = new AtomicInteger();
        AtomicLong firstDeltaAt = new AtomicLong(0);
        AtomicReference<Msg> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        StreamOptions options = StreamOptions.builder()
                .eventTypes(EventType.ALL)
                .incremental(false)
                .includeReasoningChunk(true)
                .includeSummaryChunk(true)
                .build();

        Disposable subscription = agent.stream(messages, options)
                .timeout(Duration.ofMillis(properties.getChat().getHardTimeoutMs()))
                .subscribe(event -> onEvent(event, accumulated, index, result, sink, plan,
                                firstDeltaAt, t0),
                        error -> { failure.set(error); cancellation.finish(); },
                        cancellation::finish);
        cancellation.bind(subscription);

        try {
            if (!cancellation.await(properties.getChat().getSseTimeoutMs())) {
                failure.compareAndSet(null, new java.util.concurrent.TimeoutException("SSE wait timed out"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, e);
        }
        if (cancellation.isCancelled()) {
            // CR-053：cancel()/bind() 已 dispose 订阅，直接返回已累积的半截正文，不再补齐 finalMsg
            subscription.dispose();
            return new Generation(accumulated.get(), 0, 0, 0, 0, firstDeltaAt.get());
        }
        if (failure.get() != null) {
            // CR-049 / D-15：超时/异常后取消订阅，避免底层生成线程继续把 delta 推给已关闭的 emitter
            subscription.dispose();
            throw new IllegalStateException("MODEL_CALL_FAILED", failure.get());
        }

        Msg finalMsg = result.get();
        String full = trimForMode(finalMsg == null ? null : finalMsg.getTextContent(), plan);
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
        // CR-065：DeepSeek 兼容网关回传缓存命中 token（prompt_cache_hit_tokens /
        // prompt_tokens_details.cached_tokens），AgentScope 已解析为 ChatUsage.cachedTokens。
        // 网关不透传时为 0，此时 miss = prompt；异常值由 CallLogRecorder 收敛到 [0, prompt]。
        int cacheHit = usage == null ? 0 : Math.max(0, usage.getCachedTokens());
        int cacheMiss = Math.max(0, prompt - Math.min(cacheHit, prompt));
        return new Generation(text, prompt, completion, cacheHit, cacheMiss, firstDeltaAt.get());
    }

    /** 前缀差分：无论框架给累积文本还是增量，都能推出不重复的 delta。 */
    private void onEvent(Event event, AtomicReference<String> accumulated, AtomicInteger index,
                         AtomicReference<Msg> result, SolutionStreamSink sink, AnswerPlan plan,
                         AtomicLong firstDeltaAt, long t0) {
        Msg msg = event.getMessage();
        if (msg == null) {
            return;
        }
        if (event.getType() == EventType.AGENT_RESULT) {
            result.set(msg);
            return;
        }
        String chunk = trimForMode(msg.getTextContent(), plan);
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

    /**
     * 仅思路模式：后端强制裁掉第一个被排除段及之后的内容，不依赖模型自觉遵守（AC-108-1）。
     *
     * <p>CR-043：切断点每帧从 plan 现取——模型若在本轮通过 {@code subject_get} 换成自定义结构，
     * 之后的增量按新结构的排除点裁剪；改造前用生成前的快照，AUTO + 仅思路 + 自定义结构会漏裁。</p>
     */
    private static String trimForMode(String text, AnswerPlan plan) {
        String cutTitle = plan.cutTitle();
        if (text == null || cutTitle == null) {
            return text;
        }
        int cut = text.indexOf(cutTitle);
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

    /** 记录首个可见字符相对 t0 的耗时（TTFT）。 */
    private static void markFirstDelta(AtomicLong firstDeltaAt, long t0) {
        firstDeltaAt.compareAndSet(0, System.currentTimeMillis() - t0);
    }

    private record Generation(String text, int promptTokens, int completionTokens,
                              int cacheHitTokens, int cacheMissTokens, long ttftMs) {
    }
}
