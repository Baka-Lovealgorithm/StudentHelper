package com.ai.studenthelper.infra.log;

import com.ai.studenthelper.domain.CallLog;
import com.ai.studenthelper.infra.model.ModelConfigResolver;
import com.ai.studenthelper.infra.persistence.CallLogRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 调用日志落库（FR-111）；不记录 apiKey / 完整 Prompt。 */
@Component
public class CallLogRecorder {

    private final CallLogRepository repository;

    public CallLogRecorder(CallLogRepository repository) {
        this.repository = repository;
    }

    public void record(Long messageId, ModelConfigResolver.ResolvedModelConfig config,
                       Integer promptTokens, Integer completionTokens, long durationMs,
                       String status, String errorSummary) {
        record(messageId, config, promptTokens, completionTokens, durationMs, status, errorSummary,
                config != null && config.debug());
    }

    /** CR-018：带学科命中理由的调用日志；CR-065：缓存 token 由新重载承接，此处写 0。 */
    public void record(Long messageId, ModelConfigResolver.ResolvedModelConfig config,
                       Integer promptTokens, Integer completionTokens, long durationMs,
                       String status, String errorSummary, boolean debug, String subjectHit) {
        record(messageId, config, promptTokens, completionTokens, 0, 0, durationMs,
                status, errorSummary, debug, subjectHit);
    }

    /** CR-014：调试 / 桩服务调用带 is_debug=1，看板聚合恒过滤掉这些行。 */
    public void record(Long messageId, ModelConfigResolver.ResolvedModelConfig config,
                       Integer promptTokens, Integer completionTokens, long durationMs,
                       String status, String errorSummary, boolean debug) {
        record(messageId, config, promptTokens, completionTokens, 0, 0, durationMs,
                status, errorSummary, debug, null);
    }

    /**
     * CR-065：完整调用日志，额外记录网关回传的缓存命中 / 未命中 prompt token。
     *
     * <p>失败 / 中断轮由调用方传 0；{@code cacheHitTokens} 会被收敛到 [0, promptTokens]，
     * 避免网关给出大于总输入 token 的异常值污染看板命中率。</p>
     */
    public void record(Long messageId, ModelConfigResolver.ResolvedModelConfig config,
                       Integer promptTokens, Integer completionTokens,
                       Integer cacheHitTokens, Integer cacheMissTokens, long durationMs,
                       String status, String errorSummary, boolean debug, String subjectHit) {
        int prompt = promptTokens == null ? 0 : Math.max(0, promptTokens);
        int hit = cacheHitTokens == null ? 0 : Math.max(0, Math.min(cacheHitTokens, prompt));
        int miss = cacheMissTokens == null ? 0 : Math.max(0, cacheMissTokens);
        recordInternal(messageId, config, promptTokens, completionTokens, hit, miss, durationMs,
                status, errorSummary, debug, subjectHit);
    }

    private void recordInternal(Long messageId, ModelConfigResolver.ResolvedModelConfig config,
                                Integer promptTokens, Integer completionTokens,
                                int cacheHitTokens, int cacheMissTokens, long durationMs,
                                String status, String errorSummary, boolean debug, String subjectHit) {
        CallLog log = new CallLog();
        log.setMessageId(messageId);
        log.setModelConfigId(config == null ? null : config.id());
        log.setModelName(config == null ? null : config.modelName());
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setCacheHitTokens(cacheHitTokens);
        log.setCacheMissTokens(cacheMissTokens);
        log.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        log.setStatus(status);
        log.setIsDebug(debug ? 1 : 0);
        log.setErrorSummary(errorSummary == null ? null : truncate(errorSummary, 500));
        log.setSubjectHit(subjectHit == null ? null : truncate(subjectHit, 500));
        log.setCreatedAt(LocalDateTime.now());
        repository.save(log);
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
