package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.ActiveGenerationRegistry;
import com.ai.studenthelper.application.ChatApplicationService;
import com.ai.studenthelper.application.GenerationCancellation;
import com.ai.studenthelper.application.SolutionStreamSink;
import com.ai.studenthelper.common.dto.ChatCommand;
import com.ai.studenthelper.config.AppProperties;
import com.ai.studenthelper.common.dto.ContentPart;
import com.ai.studenthelper.common.dto.DonePayload;
import com.ai.studenthelper.common.dto.ImagePayload;
import com.ai.studenthelper.common.dto.ProcessItem;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.ImageFileValidator;
import com.ai.studenthelper.domain.enums.AgentStage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** 解题主链路 SSE 接口（FR-101~103、106、109、110）。 */
@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    /**
     * CR-049 / D-15：emitter 比应用层 latch 晚 15s 超时，给 handleFailure 的 error 事件留窗口。
     * 改造前两者同为 60s，超时瞬间 emitter 已 complete，error 发不出去。
     */
    static final long EMITTER_GRACE_MS = 15_000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatApplicationService service;
    private final AppProperties properties;
    private final ActiveGenerationRegistry registry;

    public ChatController(ChatApplicationService service, AppProperties properties,
                          ActiveGenerationRegistry registry) {
        this.service = service;
        this.properties = properties;
        this.registry = registry;
    }

    @PostMapping(value = "/api/chat/stream",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("sessionId") Long sessionId,
                             @RequestParam(value = "text", required = false) String text,
                             @RequestParam(value = "hint", required = false) String hint,
                             @RequestParam(value = "guideMode", defaultValue = "0") int guideMode,
                             @RequestParam(value = "parts", required = false) String partsJson,
                             @RequestParam(value = "practice", defaultValue = "false") boolean practice,
                             @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        SseEmitter emitter = new SseEmitter(
                properties.getChat().getSseTimeoutMs() + EMITTER_GRACE_MS);
        SseSink sink = new SseSink(emitter);
        // CR-053：把本轮生成注册进会话级注册表，供 /api/chat/cancel 按 sessionId 取消
        GenerationCancellation cancellation = new GenerationCancellation();
        registry.register(sessionId, cancellation);
        CompletableFuture<Void> future;
        try {
            List<ImagePayload> payloads = readImages(images);
            ChatCommand command = new ChatCommand(sessionId, text, hint, guideMode,
                    payloads, readParts(partsJson), practice);
            future = CompletableFuture.runAsync(() -> service.solve(command, sink, cancellation))
                    .exceptionally(error -> {
                        emitUnexpectedFailure(sink, error);
                        return null;
                    });
        } catch (BusinessException e) {
            registry.unregister(sessionId, cancellation);
            sink.onError(e.getErrorCode(), e.getDetail());
            return emitter;
        } catch (Exception e) {
            registry.unregister(sessionId, cancellation);
            log.error("解题请求预处理失败", e);
            sink.onError(ErrorCode.E5000, null);
            return emitter;
        }
        future.whenComplete((ignored, error) -> registry.unregister(sessionId, cancellation));
        return emitter;
    }

    /** CR-053：停止指定会话当前正在生成的回答；无运行中生成时返回 false，不抛业务异常。 */
    @PostMapping("/api/chat/cancel")
    public ApiResponse<Boolean> cancel(@RequestParam("sessionId") Long sessionId) {
        return ApiResponse.ok(registry.cancel(sessionId));
    }

    /**
     * 异步线程未捕获异常的最后兜底：仍要发一条 error，不能让 emitter 静默挂到 60s 超时（FR-110 / TCR-014）。
     * 正常失败路径由 ChatApplicationService.handleFailure 发出，本方法只在异常逃出 solve 时触发。
     */
    static void emitUnexpectedFailure(SolutionStreamSink sink, Throwable error) {
        Throwable root = error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
        log.error("解题异步线程未捕获异常", root);
        try {
            sink.onError(ErrorCode.E5000, null);
        } catch (Exception e) {
            log.warn("兜底失败事件推送异常 reason={}", e.getMessage());
        }
    }

    /** 前端富文本输入框按输入顺序上报的图文段；缺失或为空时退回「整段文字 + 图片列表」。 */
    private List<ContentPart> readParts(String partsJson) {
        if (partsJson == null || partsJson.isBlank()) {
            return List.of();
        }
        try {
            List<ContentPart> parts = MAPPER.readValue(partsJson, new TypeReference<List<ContentPart>>() { });
            return parts == null ? List.of() : parts;
        } catch (Exception e) {
            log.warn("parts 解析失败，退回按整段文字处理 reason={}", e.getMessage());
            throw new BusinessException(ErrorCode.E1001, "图文顺序格式不正确，请重新输入");
        }
    }

    private List<ImagePayload> readImages(List<MultipartFile> images) throws IOException {
        List<ImagePayload> result = new ArrayList<>();
        if (images == null) {
            return result;
        }
        for (MultipartFile file : images) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new BusinessException(ErrorCode.E1002, "单张图片不能超过 10 MB");
            }
            byte[] bytes = file.getBytes();
            String mime = ImageFileValidator.detectMime(bytes);
            if (mime == null) {
                throw new BusinessException(ErrorCode.E1002);
            }
            result.add(new ImagePayload(file.getOriginalFilename(), mime,
                    ImageFileValidator.extensionFor(mime), bytes));
        }
        return result;
    }

    /** 把应用层事件适配为 SSE 协议；失败只发 error，不发 done。 */
    private static final class SseSink implements SolutionStreamSink {

        private final SseEmitter emitter;
        /** done / error 只允许发一次，避免异常兜底与正常路径重复 complete（TCR-014）。 */
        private final AtomicBoolean finished = new AtomicBoolean(false);

        private SseSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onStatus(AgentStage stage) {
            send("status", Map.of("stage", stage.name()));
        }

        @Override
        public void onDelta(String text, int index) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("text", text);
            data.put("index", index);
            send("delta", data);
        }

        @Override
        public void onProcess(ProcessItem item) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("seq", item.seq());
            data.put("kind", item.kind());
            data.put("title", item.title());
            data.put("detail", item.detail() == null ? "" : item.detail());
            data.put("elapsedMs", item.elapsedMs());
            send("process", data);
        }

        @Override
        public void onDone(DonePayload payload) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("messageId", payload.messageId());
            data.put("promptTokens", payload.promptTokens());
            data.put("completionTokens", payload.completionTokens());
            data.put("durationMs", payload.durationMs());
            data.put("ttftMs", payload.ttftMs());
            send("done", data);
            emitter.complete();
        }

        @Override
        public void onError(ErrorCode code, String detail) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", code.getCode());
            data.put("message", detail == null ? code.getDefaultMessage() : detail);
            send("error", data);
            emitter.complete();
        }

        private void send(String event, Object data) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException e) {
                log.warn("SSE 事件发送失败 event={} reason={}", event, e.getMessage());
            }
        }
    }
}
