package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.LearningStatsApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.LearningStatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** T-305 学情统计接口。 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final LearningStatsApplicationService service;

    public StatsController(LearningStatsApplicationService service) {
        this.service = service;
    }

    @GetMapping("/learning")
    public ApiResponse<LearningStatsResponse> learning() {
        return ApiResponse.ok(service.summary());
    }
}
