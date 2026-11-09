package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.ProfileApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.MemoryRequest;
import com.ai.studenthelper.common.dto.MemoryResponse;
import com.ai.studenthelper.common.dto.ProfileResponse;
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

/** T-201 画像与记忆接口（显式写入为主，DD-05）。 */
@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileApplicationService service;

    public ProfileController(ProfileApplicationService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> profile() {
        return ApiResponse.ok(service.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<ProfileResponse> update(@RequestBody Map<String, Object> patch) {
        return ApiResponse.ok(service.updateProfile(patch));
    }

    @DeleteMapping("/profile")
    public ApiResponse<Boolean> clear() {
        service.clearProfile();
        return ApiResponse.ok(true);
    }

    @GetMapping("/memories")
    public ApiResponse<List<MemoryResponse>> memories() {
        return ApiResponse.ok(service.memories());
    }

    @PostMapping("/memories")
    public ApiResponse<MemoryResponse> add(@RequestBody MemoryRequest request) {
        return ApiResponse.ok(service.addMemory(request));
    }

    @DeleteMapping("/memories/{id}")
    public ApiResponse<Boolean> disable(@PathVariable Long id) {
        service.disableMemory(id);
        return ApiResponse.ok(true);
    }
}
