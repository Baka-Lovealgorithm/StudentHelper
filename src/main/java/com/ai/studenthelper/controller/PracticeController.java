package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.PracticeApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.PracticeAnswerRequest;
import com.ai.studenthelper.common.dto.PracticeGenerateRequest;
import com.ai.studenthelper.common.dto.PracticeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** T-205 举一反三四步接口。 */
@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeApplicationService service;

    public PracticeController(PracticeApplicationService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public ApiResponse<PracticeResponse> generate(@RequestBody PracticeGenerateRequest request) {
        return ApiResponse.ok(service.generate(request.sourceMessageId(), request.kpId()));
    }

    @PostMapping("/{id}/answer")
    public ApiResponse<PracticeResponse> answer(@PathVariable Long id,
                                                @RequestBody PracticeAnswerRequest request) {
        return ApiResponse.ok(service.answer(id, request.userAnswer()));
    }

    @PostMapping("/{id}/judge")
    public ApiResponse<PracticeResponse> judge(@PathVariable Long id) {
        return ApiResponse.ok(service.judge(id));
    }

    @GetMapping
    public ApiResponse<List<PracticeResponse>> list(@RequestParam(required = false) Long sessionId) {
        return ApiResponse.ok(service.list(sessionId));
    }
}
