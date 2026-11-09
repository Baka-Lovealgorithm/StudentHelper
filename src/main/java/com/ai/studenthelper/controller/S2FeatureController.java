package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.S2FeatureService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.FeatureToggleRequest;
import com.ai.studenthelper.common.dto.FeatureToggleResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** S2 功能开关查询与切换（DD-01）。开关本身属于 S2 治理入口，始终可访问。 */
@RestController
@RequestMapping("/api/features")
public class S2FeatureController {

    private final S2FeatureService service;

    public S2FeatureController(S2FeatureService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<FeatureToggleResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PutMapping("/{key}")
    public ApiResponse<FeatureToggleResponse> update(@PathVariable String key,
                                                     @Valid @RequestBody FeatureToggleRequest request) {
        return ApiResponse.ok(service.update(key, request.enabled()));
    }
}
