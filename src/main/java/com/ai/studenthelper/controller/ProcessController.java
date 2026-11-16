package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.ProcessApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.ProcessItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CR-027：按会话回读模型行为过程，供前端按 messageId 分组渲染。 */
@RestController
@RequestMapping("/api/process")
public class ProcessController {

    private final ProcessApplicationService service;

    public ProcessController(ProcessApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ProcessItem>> list(@RequestParam Long sessionId) {
        return ApiResponse.ok(service.list(sessionId));
    }
}
