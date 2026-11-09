package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.FeedbackApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.FeedbackRequest;
import com.ai.studenthelper.common.dto.FeedbackResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 反馈标记接口（FR-207 / T-207）；与 T-202 错题本共用 t_message_feedback。 */
@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final FeedbackApplicationService service;

    public FeedbackController(FeedbackApplicationService service) {
        this.service = service;
    }

    @PostMapping("/feedback")
    public ApiResponse<FeedbackResponse> mark(@RequestBody FeedbackRequest request) {
        return ApiResponse.ok(service.mark(request));
    }

    @GetMapping("/feedback")
    public ApiResponse<List<FeedbackResponse>> list(
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "kind", required = false) String kind) {
        return ApiResponse.ok(service.list(sessionId, kind));
    }
}
