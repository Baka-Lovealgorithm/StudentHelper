package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.SubjectSkillApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.SubjectSkillEnableRequest;
import com.ai.studenthelper.common.dto.SubjectSkillRequest;
import com.ai.studenthelper.common.dto.SubjectSkillResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CR-018：学科规范管理接口（内置只读可停用、自定义 CRUD、SKILL.md 文件夹导入）。 */
@RestController
@RequestMapping("/api/subject-skills")
public class SubjectSkillController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SubjectSkillApplicationService service;

    public SubjectSkillController(SubjectSkillApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SubjectSkillResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<SubjectSkillResponse> create(@RequestBody(required = false) SubjectSkillRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SubjectSkillResponse> update(@PathVariable Long id,
                                                     @RequestBody(required = false) SubjectSkillRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<SubjectSkillResponse> setEnabled(@PathVariable Long id,
                                                         @RequestBody(required = false) SubjectSkillEnableRequest request) {
        return ApiResponse.ok(service.setEnabled(id, request == null ? null : request.enabled()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    /** 文件夹导入：files[] 与 paths（webKitRelativePath 的 JSON 数组）一一对应。 */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SubjectSkillResponse> importFolder(
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "paths", required = false) String pathsJson) throws IOException {
        List<String> paths;
        try {
            paths = pathsJson == null || pathsJson.isBlank()
                    ? List.of() : MAPPER.readValue(pathsJson, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.E1004, "paths 必须是相对路径的 JSON 数组");
        }
        List<byte[]> bytes = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                bytes.add(file.getBytes());
            }
        }
        return ApiResponse.ok(service.importFromFolder(bytes, paths));
    }
}
