package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.ModelConnectivityService;
import com.ai.studenthelper.application.ModelProviderApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.FetchModelsResponse;
import com.ai.studenthelper.common.dto.ModelEntryRequest;
import com.ai.studenthelper.common.dto.ModelTestResult;
import com.ai.studenthelper.common.dto.ProviderListResponse;
import com.ai.studenthelper.common.dto.ProviderModelResponse;
import com.ai.studenthelper.common.dto.ProviderOptionsResponse;
import com.ai.studenthelper.common.dto.ProviderRequest;
import com.ai.studenthelper.common.dto.ProviderTestResponse;
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

/** CR-012 通用厂商配置接口；列表与响应只给 hasApiKey，绝不回显 Key。 */
@RestController
@RequestMapping("/api/model-providers")
public class ModelProviderController {

    private final ModelProviderApplicationService service;
    private final ModelConnectivityService connectivity;

    public ModelProviderController(ModelProviderApplicationService service,
                                   ModelConnectivityService connectivity) {
        this.service = service;
        this.connectivity = connectivity;
    }

    @GetMapping
    public ApiResponse<ProviderListResponse> list() {
        return ApiResponse.ok(service.list());
    }

    /** 对话页两级下拉（字面量路径，不与 /{id} 冲突）。 */
    @GetMapping("/options")
    public ApiResponse<ProviderOptionsResponse> options() {
        return ApiResponse.ok(service.options());
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@RequestBody ProviderRequest request) {
        return ApiResponse.ok(Map.of("id", service.createProvider(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Long>> update(@PathVariable Long id,
                                                 @RequestBody ProviderRequest request) {
        service.updateProvider(id, request);
        return ApiResponse.ok(Map.of("id", id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id) {
        service.deleteProvider(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    /** 后端代理 GET {baseUrl}/models，Key 只放在 Authorization 头。 */
    @PostMapping("/{id}/fetch-models")
    public ApiResponse<FetchModelsResponse> fetchModels(@PathVariable Long id) {
        return ApiResponse.ok(service.fetchModels(id));
    }

    @PostMapping("/{id}/models")
    public ApiResponse<List<ProviderModelResponse>> addModels(@PathVariable Long id,
                                                              @RequestBody ModelEntryRequest request) {
        return ApiResponse.ok(service.addModels(id, request));
    }

    @PutMapping("/{pid}/models/{mid}")
    public ApiResponse<ProviderModelResponse> updateModel(@PathVariable Long pid,
                                                          @PathVariable Long mid,
                                                          @RequestBody ModelEntryRequest request) {
        return ApiResponse.ok(service.updateModel(pid, mid, request));
    }

    @DeleteMapping("/{pid}/models/{mid}")
    public ApiResponse<Map<String, Boolean>> deleteModel(@PathVariable Long pid,
                                                         @PathVariable Long mid) {
        service.deleteModel(pid, mid);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PostMapping("/{pid}/models/{mid}/default")
    public ApiResponse<Map<String, Boolean>> setDefault(@PathVariable Long pid,
                                                        @PathVariable Long mid) {
        service.setDefault(pid, mid);
        return ApiResponse.ok(Map.of("defaulted", true));
    }

    /** CR-020：单模型连通性测试（真实最小对话请求；结果不落库、不计入看板）。 */
    @PostMapping("/{pid}/models/{mid}/test")
    public ApiResponse<ModelTestResult> testModel(@PathVariable Long pid, @PathVariable Long mid) {
        return ApiResponse.ok(connectivity.testModel(pid, mid));
    }

    /** CR-020：厂商全部模型连通性测试（并发 4、单项 15s、整体 40s）。 */
    @PostMapping("/{pid}/test")
    public ApiResponse<ProviderTestResponse> testProvider(@PathVariable Long pid) {
        return ApiResponse.ok(connectivity.testProvider(pid));
    }
}
