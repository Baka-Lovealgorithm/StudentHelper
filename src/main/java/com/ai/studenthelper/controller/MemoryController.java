package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.MemoryApplicationService;
import com.ai.studenthelper.application.MemoryConsolidationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.MemoryOverviewResponse;
import com.ai.studenthelper.common.dto.MemoryUserUpdateRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** T-401：长期记忆文件接口（md 双层存储）。 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryApplicationService service;

    public MemoryController(MemoryApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<MemoryOverviewResponse> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/file")
    public ApiResponse<String> file(@RequestParam("name") String name) {
        return ApiResponse.ok(service.readFile(name));
    }

    @PutMapping("/user")
    public ApiResponse<MemoryOverviewResponse> updateUser(@RequestBody MemoryUserUpdateRequest request) {
        return ApiResponse.ok(service.updateUserSection(request));
    }

    @PostMapping("/consolidate")
    public ApiResponse<MemoryConsolidationService.ConsolidationResult> consolidate() {
        return ApiResponse.ok(service.consolidate());
    }

    @DeleteMapping
    public ApiResponse<Boolean> clear() {
        service.clear();
        return ApiResponse.ok(true);
    }
}
