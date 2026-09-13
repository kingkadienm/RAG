package com.wangzs.rag.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 校验状态枚举
 */
@Getter
@AllArgsConstructor
public enum CheckStatusEnum {

    PASSED(1, "通过"),
    REJECTED(2, "拒绝");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    public static CheckStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}
