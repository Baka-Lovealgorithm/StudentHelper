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
        CallLog log = new CallLog();
        log.setMessageId(messageId);
        log.setModelConfigId(config == null ? null : config.id());
        log.setModelName(config == null ? null : config.modelName());
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        log.setStatus(status);
        log.setErrorSummary(errorSummary == null ? null : truncate(errorSummary, 500));
        log.setCreatedAt(LocalDateTime.now());
        repository.save(log);
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
