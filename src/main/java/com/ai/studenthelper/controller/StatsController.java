package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.LearningStatsApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.LearningStatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** T-305 学情统计接口。CR-023：支持 `?days=0|1|3|7`（缺省 7）。 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final LearningStatsApplicationService service;

    public StatsController(LearningStatsApplicationService service) {
        this.service = service;
    }

    @GetMapping("/learning")
    public ApiResponse<LearningStatsResponse> learning(
            @RequestParam(value = "days", required = false) Integer days) {
        return ApiResponse.ok(service.summary(days));
    }
}
