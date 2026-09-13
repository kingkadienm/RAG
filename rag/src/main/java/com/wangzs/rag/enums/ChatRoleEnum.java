package com.wangzs.rag.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 对话消息角色枚举
 */
@Getter
@AllArgsConstructor
public enum ChatRoleEnum {

    USER(1, "用户"),
    ASSISTANT(2, "助手"),
    SYSTEM(3, "系统");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    public static ChatRoleEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}
