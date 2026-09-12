package com.wangzs.rag.common.result;

import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一捕获异常并包装为 ApiResult 格式返回给前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常（BizException）
     */
    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@Valid 触发）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        log.warn("参数校验异常: {}", message);
        return ApiResult.fail(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ApiResult<Void> handleRuntimeException(RuntimeException e) {
        log.error("系统异常", e);
        return ApiResult.fail(ErrorCode.SYSTEM_ERROR.getCode(), e.getMessage());
    }

    /**
     * 其他异常
     */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception e) {
        log.error("未知异常", e);
        return ApiResult.fail(ErrorCode.SYSTEM_ERROR.getCode(), "系统繁忙，请稍后重试");
    }
}
