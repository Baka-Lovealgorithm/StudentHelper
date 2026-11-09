package com.ai.studenthelper.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一异常处理：错误码 + 可直接理解的中文提示，禁止外泄堆栈 / SQL / 密钥（NFR-108、AC-110-1）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("业务异常 code={} detail={}", ex.getErrorCode().getCode(), ex.getDetail());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.fail(ex.getErrorCode(), ex.getDetail()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception ex) {
        log.warn("参数校验失败：{}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.E1004.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.E1004, null));
    }

    /** 未知路径必须保持 404，不能被下面的 Exception 兜底器误判为内部错误（TCR-012）。 */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        log.debug("请求资源不存在：{}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.E1005.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.E1005, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未知内部错误", ex);
        return ResponseEntity.status(ErrorCode.E5000.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.E5000, null));
    }
}
