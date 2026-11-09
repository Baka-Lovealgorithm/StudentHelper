package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.SessionApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.SessionCreateRequest;
import com.ai.studenthelper.common.dto.SessionDetailResponse;
import com.ai.studenthelper.common.dto.SessionGuideModeRequest;
import com.ai.studenthelper.common.dto.SessionPageResponse;
import com.ai.studenthelper.common.dto.SessionRenameRequest;
import com.ai.studenthelper.common.dto.SessionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 会话与历史接口（FR-104）。 */
@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionApplicationService service;

    public SessionController(SessionApplicationService service) {
        this.service = service;
    }

    @PostMapping("/sessions")
    public ApiResponse<SessionResponse> create(@RequestBody(required = false) SessionCreateRequest request) {
        return ApiResponse.ok(service.create(request == null ? null : request.title()));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    /** 会话分页（前端「加载更多」专用）；字面量路径优先于 /sessions/{id} 匹配。 */
    @GetMapping("/sessions/page")
    public ApiResponse<SessionPageResponse> listPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        return ApiResponse.ok(service.listPage(safePage, safeSize));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<SessionDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @DeleteMapping("/messages/{id}")
    public ApiResponse<Map<String, Boolean>> deleteMessage(@PathVariable Long id) {
        service.deleteMessage(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PatchMapping("/sessions/{id}/guide-mode")
    public ApiResponse<SessionResponse> updateGuideMode(@PathVariable Long id,
                                                        @Valid @RequestBody SessionGuideModeRequest request) {
        return ApiResponse.ok(service.updateGuideMode(id, request.guideMode()));
    }

    @PatchMapping("/sessions/{id}/title")
    public ApiResponse<SessionResponse> rename(@PathVariable Long id,
                                               @Valid @RequestBody SessionRenameRequest request) {
        return ApiResponse.ok(service.rename(id, request.title()));
    }
}
