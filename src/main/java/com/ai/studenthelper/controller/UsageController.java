package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.UsageApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.UsageSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用量看板接口（FR-206 / T-206）：按模型聚合，不做金额换算。 */
@RestController
@RequestMapping("/api")
public class UsageController {

    private final UsageApplicationService service;

    public UsageController(UsageApplicationService service) {
        this.service = service;
    }

    @GetMapping("/usage/summary")
    public ApiResponse<List<UsageSummaryResponse>> summary() {
        return ApiResponse.ok(service.summary());
    }
}
