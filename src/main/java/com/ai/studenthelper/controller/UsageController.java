package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.UsageApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.UsageSummaryResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用量看板接口（FR-206 / T-206 / CR-014）：按模型聚合 + 按天分桶，不做金额换算。 */
@RestController
@RequestMapping("/api")
public class UsageController {

    private final UsageApplicationService service;

    public UsageController(UsageApplicationService service) {
        this.service = service;
    }

    /** days 省略 / 0 = 全部；1 / 3 / 7 = 最近 N 天滚动窗口，其它值返回 E1004。 */
    @GetMapping("/usage/summary")
    public ApiResponse<UsageSummaryResult> summary(@RequestParam(required = false) Integer days) {
        return ApiResponse.ok(service.summary(days));
    }
}
