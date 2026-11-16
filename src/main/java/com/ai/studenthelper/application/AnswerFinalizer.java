package com.ai.studenthelper.application;

import com.ai.studenthelper.agent.KnowledgeExtractor;
import com.ai.studenthelper.agent.SolutionSectionParser;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.SectionSchema;
import com.ai.studenthelper.common.SessionTitleParser;
import com.ai.studenthelper.common.dto.DonePayload;
import com.ai.studenthelper.domain.Message;
import com.ai.studenthelper.domain.enums.MessageRole;
import com.ai.studenthelper.domain.enums.MessageStatus;
import com.ai.studenthelper.infra.log.CallLogRecorder;
import com.ai.studenthelper.infra.persistence.MessageRepository;
import com.ai.studenthelper.infra.persistence.ProcessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成后的收尾（CR-043 / 候选 02）。
 *
 * <p>从 {@code solve()} 切出来的第二段：标题剥离 → 结构解析 → 三次落库 → 计时与调用日志 →
 * 下发 done → 知识点抽取。改造前这些与「装配 / 生成」依次排在一个方法里，
 * 现在只依赖 {@link ChatTurn} 与生成结果，落库口径集中在这里一处。</p>
 */
@Service
public class AnswerFinalizer {

    private static final Logger log = LoggerFactory.getLogger(AnswerFinalizer.class);

    private final MessageRepository messageRepository;
    private final SessionApplicationService sessionService;
    private final SolutionSectionParser parser;
    private final CallLogRecorder logRecorder;
    private final KnowledgeExtractor knowledgeExtractor;
    private final ProcessRepository processRepository;

    public AnswerFinalizer(MessageRepository messageRepository,
                           SessionApplicationService sessionService,
                           SolutionSectionParser parser,
                           CallLogRecorder logRecorder,
                           KnowledgeExtractor knowledgeExtractor,
                           ProcessRepository processRepository) {
        this.messageRepository = messageRepository;
        this.sessionService = sessionService;
        this.parser = parser;
        this.logRecorder = logRecorder;
        this.knowledgeExtractor = knowledgeExtractor;
        this.processRepository = processRepository;
    }

    /** 兼容既有调用：缓存 token 写 0。 */
    public Message finalize(ChatTurn turn, String answer, int promptTokens, int completionTokens,
                            long ttftMs, SolutionStreamSink sink) {
        return finalize(turn, answer, promptTokens, completionTokens, 0, 0, ttftMs, sink);
    }

    /**
     * 落库助手消息、下发 done、抽取知识点；返回已保存的消息。
     *
     * @param answer           模型完整输出（含标题行，未解析）
     * @param promptTokens     输入 token
     * @param completionTokens 输出 token
     * @param cacheHitTokens   CR-065：缓存命中 prompt token
     * @param cacheMissTokens  CR-065：缓存未命中 prompt token
     * @param ttftMs           首字耗时
     */
    public Message finalize(ChatTurn turn, String answer, int promptTokens, int completionTokens,
                            int cacheHitTokens, int cacheMissTokens, long ttftMs,
                            SolutionStreamSink sink) {
        AnswerPlan plan = turn.prepared().plan();
        var config = turn.prepared().config();
        SubjectSkillRuntime runtime = turn.prepared().runtime();

        // CR-033：【会话标题】行剥离；落库与复制口径都不含标记，标题行不拦截流式
        SessionTitleParser.Result titleResult = SessionTitleParser.extract(answer);
        // CR-047：作图工具已生成但正文没引用的图片在这里补齐，保证「画了就能看见」
        String answerBody = ensureDiagramLinks(titleResult.stripped(),
                turn.processRecorder().diagramLinks());

        // 工具可能在生成过程中按需读入规范，这里取生成结束后的最终结构
        SectionSchema finalSchema = plan.currentSchema();
        SolutionSectionParser.ParseResult parsed = plan.followUp()
                ? null : parser.parse(answerBody, plan.parseTitles(finalSchema));
        String stored = parser.appendDisclaimer(answerBody);

        Message assistant = newAssistantMessage(turn, stored, promptTokens, completionTokens,
                plan.storedSchema(finalSchema), plan.subjectLabel(finalSchema), plan.intent().name());
        Message savedAssistant = messageRepository.save(assistant);

        // CR-027：过程与助手消息绑定后落库，刷新 / 切换会话可回读
        processRepository.saveAll(savedAssistant.getId(), turn.processRecorder().finish());
        turn.userMessage().setStatus(MessageStatus.SUCCESS.name());
        messageRepository.save(turn.userMessage());

        // CR-033：首轮标题写回；仅默认标题时生效，失败不影响本轮结果
        if (plan.autoTitle() && titleResult.title() != null) {
            sessionService.updateAutoTitle(turn.command().sessionId(), titleResult.title());
        }

        // t2 = 落库完成；t_message 与 t_call_log 共用同一 durationMs，done 在两者之后发送
        long durationMs = System.currentTimeMillis() - turn.startedAt();
        savedAssistant.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        messageRepository.save(savedAssistant);
        logRecorder.record(savedAssistant.getId(), config, promptTokens, completionTokens,
                cacheHitTokens, cacheMissTokens, durationMs, "SUCCESS", null,
                config != null && config.debug(), runtime.auditHit());
        log.info("CHAT_TIMING sessionId={} messageId={} ttftMs={} totalMs={} model={} subject={} intent={} cacheHit={} cacheMiss={}",
                turn.command().sessionId(), savedAssistant.getId(), ttftMs, durationMs,
                config.modelName(), runtime.hitCode(), plan.intent(), cacheHitTokens, cacheMissTokens);

        sink.onDone(new DonePayload(savedAssistant.getId(), promptTokens, completionTokens,
                durationMs, ttftMs));

        // DD-02：知识点处理在 done 之后执行，不计入 ttftMs / durationMs
        if (plan.extractKnowledge() && parsed != null && parsed.sections().size() > 1) {
            knowledgeExtractor.extractAndRecord(turn.command().sessionId(), savedAssistant.getId(),
                    knowledgeSubject(runtime), parsed.sections().get(1).body());
        }
        return savedAssistant;
    }

    /**
     * CR-053：用户主动停止生成时落库「中断消息」——半截正文 + FAILED / E1006，刷新后仍可查看。
     * 题面消息保持 SUCCESS；不追加免责声明、不抽取知识点、不写自动标题、不发 done。
     */
    public Message finalizeInterrupted(ChatTurn turn, String partialAnswer, SolutionStreamSink sink) {
        AnswerPlan plan = turn.prepared().plan();
        var config = turn.prepared().config();
        SubjectSkillRuntime runtime = turn.prepared().runtime();

        SessionTitleParser.Result titleResult = SessionTitleParser.extract(partialAnswer);
        String body = titleResult.stripped();

        Message assistant = newAssistantMessage(turn, body, 0, 0,
                plan.storedSchema(plan.currentSchema()), plan.subjectLabel(plan.currentSchema()),
                plan.intent().name());
        assistant.setStatus(MessageStatus.FAILED.name());
        assistant.setErrorCode(ErrorCode.E1006.getCode());
        Message savedAssistant = messageRepository.save(assistant);

        processRepository.saveAll(savedAssistant.getId(), turn.processRecorder().finish());
        turn.userMessage().setStatus(MessageStatus.SUCCESS.name());
        messageRepository.save(turn.userMessage());

        long durationMs = System.currentTimeMillis() - turn.startedAt();
        savedAssistant.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        messageRepository.save(savedAssistant);
        logRecorder.record(savedAssistant.getId(), config, 0, 0, durationMs, "FAILED",
                ErrorCode.E1006.getCode(), config != null && config.debug(), runtime.auditHit());
        log.info("CHAT_CANCELLED sessionId={} messageId={} totalMs={} chars={}",
                turn.command().sessionId(), savedAssistant.getId(), durationMs, body.length());

        sink.onError(ErrorCode.E1006, null);
        return savedAssistant;
    }

    /**
     * CR-047：把本轮已渲染但正文缺失的图片链接补进答案。
     *
     * <p>模型偶尔只写「已生成图片」而不贴 Markdown 链接；不兜底就会出现「工具跑了、
     * 用户看不到图」。补图优先落在 ⑤ / ⑥ 之前（即 ④ 详细步骤末尾），没有结构标题时追加到末尾。</p>
     */
    static String ensureDiagramLinks(String answer, List<String> links) {
        if (answer == null || links == null || links.isEmpty()) {
            return answer;
        }
        StringBuilder missing = new StringBuilder();
        for (String link : links) {
            if (link == null || link.isBlank()) {
                continue;
            }
            Matcher url = Pattern.compile("\\((/api/images/[^)]+)\\)").matcher(link);
            if (url.find() && answer.contains(url.group(1))) {
                continue;
            }
            missing.append('\n').append(link).append('\n');
        }
        if (missing.isEmpty()) {
            return answer;
        }
        Matcher tail = Pattern.compile("(?m)^##\\s*[⑤⑥]").matcher(answer);
        int at = tail.find() ? tail.start() : answer.length();
        String head = answer.substring(0, at);
        String rest = answer.substring(at);
        return head + "\n" + missing.toString().strip() + "\n\n" + rest;
    }

    /** CR-025：知识点归属学科——学科关闭 / OFF / AUTO 未命中一律记「通用」。 */
    static String knowledgeSubject(SubjectSkillRuntime runtime) {
        if (runtime == null || runtime.isOff()) {
            return "通用";
        }
        String name = runtime.subjectName();
        return name == null || name.isBlank() ? "通用" : name.trim();
    }

    private Message newAssistantMessage(ChatTurn turn, String content, int promptTokens,
                                        int completionTokens, String sectionSchema,
                                        String subjectLabel, String intent) {
        Message message = new Message();
        message.setSessionId(turn.command().sessionId());
        message.setRole(MessageRole.ASSISTANT.name());
        message.setContent(content);
        message.setModelConfigId(turn.prepared().config().id());
        message.setGuideMode(turn.command().guideMode());
        message.setStatus(MessageStatus.SUCCESS.name());
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setDurationMs(0);
        message.setSectionSchema(sectionSchema);
        message.setSubjectLabel(subjectLabel);
        message.setIntent(intent);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
}
