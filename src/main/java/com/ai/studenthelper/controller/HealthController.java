package com.ai.studenthelper.controller;

import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 健康检查：T-005 用于验证 Vite 代理与前后端链路贯通。 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("UP", "StudentHelper", LocalDateTime.now()));
    }
}
