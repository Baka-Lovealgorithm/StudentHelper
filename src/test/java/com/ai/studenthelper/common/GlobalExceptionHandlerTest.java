package com.ai.studenthelper.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    /**
     * 测试专用端点：S0 的 ProbeController 已按源码原意删除，
     * 这里用最小 controller 复现同样的两种异常，保证 E1001 / E5000 断言不丢失。
     */
    @RestController
    static final class TestProbeController {

        @GetMapping("/api/_probe/error")
        public void businessError() {
            throw new BusinessException(ErrorCode.E1001);
        }

        @GetMapping("/api/_probe/boom")
        public void unexpectedError() {
            throw new IllegalStateException("internal-only-detail");
        }

        @GetMapping("/api/_probe/missing")
        public void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/_probe/missing",
                    "No static resource api/_probe/missing for request '/api/_probe/missing'.");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionBecomesChineseResponseWithErrorCode() throws Exception {
        mockMvc.perform(get("/api/_probe/error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E1001"))
                .andExpect(jsonPath("$.message").value("请输入题目文字或上传图片"));
    }

    @Test
    void unexpectedExceptionBecomesE5000WithoutStackDetail() throws Exception {
        mockMvc.perform(get("/api/_probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("E5000"))
                .andExpect(jsonPath("$.message").value("发生未知错误，请查看日志"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unknownResourceStays404AndIsNotSwallowedAsE5000() throws Exception {
        mockMvc.perform(get("/api/_probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("E1005"))
                .andExpect(jsonPath("$.message").value("请求的资源不存在"));
    }
}
