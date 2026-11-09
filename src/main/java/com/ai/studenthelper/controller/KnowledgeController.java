package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.KnowledgeApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.KnowledgePointResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** T-203 知识点列表（按学科过滤）。 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeApplicationService service;

    public KnowledgeController(KnowledgeApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<KnowledgePointResponse>> list(
            @RequestParam(required = false) String subject) {
        return ApiResponse.ok(service.list(subject));
    }
}
