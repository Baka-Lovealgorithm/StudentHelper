package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.SkillApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.SkillRequest;
import com.ai.studenthelper.common.dto.SkillResponse;
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

/** T-302 解题技能接口（DD-09）。 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillApplicationService service;

    public SkillController(SkillApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SkillResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<SkillResponse> create(@RequestBody SkillRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SkillResponse> update(@PathVariable Long id, @RequestBody SkillRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
