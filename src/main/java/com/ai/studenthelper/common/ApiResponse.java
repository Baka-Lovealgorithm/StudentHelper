package com.ai.studenthelper.common;

/**
 * 统一响应包装（技术规范 3.3）。SSE 接口不使用本包装。
 */
public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>("OK", "success", null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String detail) {
        String message = (detail == null || detail.isBlank())
                ? errorCode.getDefaultMessage()
                : detail;
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }
}
