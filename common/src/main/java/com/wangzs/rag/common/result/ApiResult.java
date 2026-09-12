package com.wangzs.rag.common.result;

import com.wangzs.rag.common.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应结构
 *
 * @param <T> 数据体类型
 */
@Data
public class ApiResult<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setMessage(ErrorCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    public static <T> ApiResult<T> success(T data, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> ApiResult<T> fail(String message) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(500);
        result.setMessage(message);
        result.setData(null);
        return result;
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(null);
        return result;
    }

    public static <T> ApiResult<T> of(int code, String message, T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
}
