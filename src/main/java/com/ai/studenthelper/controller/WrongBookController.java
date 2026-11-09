package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.WrongBookApplicationService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.FeedbackResponse;
import com.ai.studenthelper.common.dto.WrongBookResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** T-202 错题本接口。 */
@RestController
@RequestMapping("/api/wrong-book")
public class WrongBookController {

    private final WrongBookApplicationService service;

    public WrongBookController(WrongBookApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<FeedbackResponse> mark(@RequestBody Map<String, Object> body) {
        Long id = body.get("messageId") instanceof Number number ? number.longValue() : null;
        String subject = body.get("subject") == null ? null : String.valueOf(body.get("subject"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        return ApiResponse.ok(service.mark(id, subject, note));
    }

    @GetMapping
    public ApiResponse<List<WrongBookResponse>> list(
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(subject, from, to, page, size));
    }
}
