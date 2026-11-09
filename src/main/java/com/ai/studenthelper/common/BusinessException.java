package com.ai.studenthelper.common;

/**
 * 业务异常：由 GlobalExceptionHandler 统一转换为含错误码的中文响应。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super((detail == null || detail.isBlank()) ? errorCode.getDefaultMessage() : detail, cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetail() {
        return detail;
    }
}
