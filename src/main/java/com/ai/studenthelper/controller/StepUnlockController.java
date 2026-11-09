package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.StepUnlockApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.StepUnlockRequest;
import com.ai.studenthelper.common.dto.StepUnlockResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** T-209 分步解锁接口（DD-07：只写解锁记录，不重新调用模型）。 */
@RestController
@RequestMapping("/api/step-unlock")
public class StepUnlockController {

    private final StepUnlockApplicationService service;

    public StepUnlockController(StepUnlockApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<StepUnlockResponse> unlock(@RequestBody StepUnlockRequest request) {
        return ApiResponse.ok(service.unlock(request.messageId(),
                request.step() == null ? 0 : request.step()));
    }

    @GetMapping
    public ApiResponse<StepUnlockResponse> state(@RequestParam("messageId") Long messageId) {
        return ApiResponse.ok(service.state(messageId));
    }
}
