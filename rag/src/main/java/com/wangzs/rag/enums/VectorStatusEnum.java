package com.wangzs.rag.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * @className: VectorStatusEnum
 * @description: 向量化状态枚举
 * @author: wangzs
 * @date: 2026-09-11 22:10
 */
@Getter
@AllArgsConstructor
public enum VectorStatusEnum {

    INIT(0, "待向量化"),
    VECTORIZING(1, "向量化中"),
    SUCCESS(2, "向量化完成"),
    FAILED(3, "向量化失败");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    public static VectorStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}
