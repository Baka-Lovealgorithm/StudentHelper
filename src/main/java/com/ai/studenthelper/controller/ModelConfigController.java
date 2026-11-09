package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.ModelConfigApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.ModelConfigRequest;
import com.ai.studenthelper.common.dto.ModelConfigResponse;
import com.ai.studenthelper.common.dto.ModelTemplateResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 模型配置接口（FR-105）；列表只返回脱敏信息。 */
@RestController
@RequestMapping("/api/model-configs")
public class ModelConfigController {

    private final ModelConfigApplicationService service;

    public ModelConfigController(ModelConfigApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ModelConfigResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/templates")
    public ApiResponse<List<ModelTemplateResponse>> templates() {
        return ApiResponse.ok(service.templates());
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(Map.of("id", service.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Long>> update(@PathVariable Long id,
                                                 @Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(Map.of("id", service.update(id, request)));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<Map<String, Boolean>> activate(@PathVariable Long id) {
        service.activate(id);
        return ApiResponse.ok(Map.of("activated", true));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
