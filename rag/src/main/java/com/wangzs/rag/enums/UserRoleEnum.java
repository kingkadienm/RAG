package com.wangzs.rag.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 用户角色枚举
 */
@Getter
@AllArgsConstructor
public enum UserRoleEnum {

    ADMIN(1, "管理员"),
    USER(2, "普通用户");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    public static UserRoleEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}
