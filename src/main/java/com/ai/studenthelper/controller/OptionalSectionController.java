package com.ai.studenthelper.controller;

import com.ai.studenthelper.application.OptionalSectionService;
import com.ai.studenthelper.common.ApiResponse;
import com.ai.studenthelper.common.dto.OptionalSectionsRequest;
import com.ai.studenthelper.common.dto.OptionalSectionsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** T-204 可选段落子开关（DD-06）。 */
@RestController
@RequestMapping("/api/optional-sections")
public class OptionalSectionController {

    private final OptionalSectionService service;

    public OptionalSectionController(OptionalSectionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<OptionalSectionsResponse> current() {
        return ApiResponse.ok(service.current());
    }

    @PutMapping
    public ApiResponse<OptionalSectionsResponse> update(@RequestBody OptionalSectionsRequest request) {
        return ApiResponse.ok(service.update(request));
    }
}
