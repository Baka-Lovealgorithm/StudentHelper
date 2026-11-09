package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.SkillApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.BusinessException;
import com.ai.studenthelper.common.ErrorCode;
import com.ai.studenthelper.common.dto.SkillEnableRequest;
import com.ai.studenthelper.common.dto.SkillRequest;
import com.ai.studenthelper.common.dto.SkillResponse;
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

/** T-302 解题技能接口（DD-09）；CR-011 新增文件夹导入与单独启停。 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * CR-011：浏览器选文件夹上传。files[] 与 paths（webKitRelativePath 的 JSON 数组）
     * 一一对应，校验与解析在 SkillFolderParser / SkillApplicationService 中完成。
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkillResponse> importFolder(
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

    @PutMapping("/{id}")
    public ApiResponse<SkillResponse> update(@PathVariable Long id, @RequestBody SkillRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /** CR-011：单独启停，不需要重传名称与正文。 */
    @PatchMapping("/{id}/enabled")
    public ApiResponse<SkillResponse> setEnabled(@PathVariable Long id,
                                                 @RequestBody(required = false) SkillEnableRequest request) {
        return ApiResponse.ok(service.setEnabled(id, request == null ? null : request.enabled()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
