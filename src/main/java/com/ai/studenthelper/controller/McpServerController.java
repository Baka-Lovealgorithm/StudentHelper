package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.McpServerApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.McpServerRequest;
import com.ai.studenthelper.common.dto.McpServerResponse;
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

/** T-301 远程 MCP Server 接口（DD-10）。 */
@RestController
@RequestMapping("/api/mcp-servers")
public class McpServerController {

    private final McpServerApplicationService service;

    public McpServerController(McpServerApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<McpServerResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<McpServerResponse> create(@RequestBody McpServerRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<McpServerResponse> update(@PathVariable Long id, @RequestBody McpServerRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<McpServerApplicationService.TestResult> test(@PathVariable Long id) {
        return ApiResponse.ok(service.test(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
