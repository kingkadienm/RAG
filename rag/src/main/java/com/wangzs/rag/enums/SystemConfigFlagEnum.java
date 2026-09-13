package com.wangzs.rag.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 系统配置标志枚举
 */
@Getter
@AllArgsConstructor
public enum SystemConfigFlagEnum {

    NO(0, "普通配置"),
    YES(1, "系统配置");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    public static SystemConfigFlagEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}
