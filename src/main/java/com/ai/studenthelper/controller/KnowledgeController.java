package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.KnowledgeApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.KnowledgeCreateRequest;
import com.ai.studenthelper.common.dto.KnowledgePageResponse;
import com.ai.studenthelper.common.dto.KnowledgePointResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** T-203 知识点列表（按学科过滤）；CR-040 起支持日期区间筛选、手动新增与删除。 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeApplicationService service;

    public KnowledgeController(KnowledgeApplicationService service) {
        this.service = service;
    }

    /**
     * CR-040：三个筛选条件同时生效。{@code from} / {@code to} 为 {@code yyyy-MM-dd}（闭区间、可只填一端）。
     */
    @GetMapping
    public ApiResponse<List<KnowledgePointResponse>> list(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.list(subject, from, to));
    }

    /** CR-025：学科去重清单，前端学科下拉使用（关闭态 E3004）。 */
    @GetMapping("/subjects")
    public ApiResponse<List<String>> subjects() {
        return ApiResponse.ok(service.subjects());
    }

    /**
     * CR-040：分页（前端「更多」专用）。默认每页 10 条；字面量路径优先于 {@code /knowledge/{id}} 匹配。
     * 筛选参数与 {@code GET /api/knowledge} 完全一致。
     */
    @GetMapping("/page")
    public ApiResponse<KnowledgePageResponse> page(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.page(subject, from, to, page, size));
    }

    /** CR-040：手动新增（命中次数固定 0，重复返回 E1004）。 */
    @PostMapping
    public ApiResponse<KnowledgePointResponse> create(@RequestBody KnowledgeCreateRequest request) {
        return ApiResponse.ok(service.create(request == null ? null : request.subject(),
                request == null ? null : request.parentName(),
                request == null ? null : request.name()));
    }

    /** CR-040：删除条目并级联清理命中明细；不存在返回 E1005。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
