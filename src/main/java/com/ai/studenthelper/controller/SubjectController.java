package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.SubjectApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.SubjectRequest;
import com.ai.studenthelper.common.dto.SubjectResponse;
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

/** T-307 学科模板接口（DD-08）。 */
@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectApplicationService service;

    public SubjectController(SubjectApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SubjectResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<SubjectResponse> create(@RequestBody SubjectRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SubjectResponse> update(@PathVariable Long id, @RequestBody SubjectRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<SubjectResponse> activate(@PathVariable Long id) {
        return ApiResponse.ok(service.activate(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
